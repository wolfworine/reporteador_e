package pe.edu.cibertec.proyemp.report;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import pe.edu.cibertec.proyemp.domain.Atributo;
import pe.edu.cibertec.proyemp.domain.CategoriaSuministro;
import pe.edu.cibertec.proyemp.domain.DetalleRol;
import pe.edu.cibertec.proyemp.domain.Reporte;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Adaptador Excel del {@link ReportGeneratorPort} usando Apache POI vigente
 * ({@code poi-ooxml} 5.x) con {@code XSSFWorkbook} (formato {@code .xlsx}),
 * en vez del {@code HSSFWorkbook} (.xls) que usaba
 * {@code ReporteManagedBean.excel()} en el legacy -- "Apache POI vigente"
 * pedido explícitamente por el título de esta feature.
 *
 * <p>Reproduce la estructura real del legacy: por cada {@link Reporte},
 * filas "Area"/"Cargo" con código+nombre, una fila de encabezados
 * (Rol/Tipo/Aplicacion/Titularidad/AtributoCodigo/AtributoNombre), y luego
 * una fila por cada {@link Atributo} de las 4 categorías
 * (Roles/Grupos/Inbox/DefaultInbox), con columnas
 * Rol/Tipo/Aplicacion/categoría/atributoCodigo/atributoNombre. A diferencia
 * del legacy, nunca escribe a {@code FileOutputStream} -- todo el workbook
 * se serializa en un {@link ByteArrayOutputStream} en memoria, y tampoco
 * repite el dedup con {@code HashSet} que tenía {@code excel()} (la lista
 * de entrada ya viene sin duplicados, ver {@code service/ReporteService}).
 *
 * <p><b>Estilos (agregados 2026-08-20, bug real reportado por el usuario
 * "no tienen forma esta todo descuadrado" comparando la vista web contra
 * el Excel):</b> la primera versión solo llamaba
 * {@code cell.setCellValue(texto)} sin ningún {@link CellStyle} -- todas
 * las columnas quedaban con el ancho default de POI (~8 caracteres),
 * cortando nombres largos de rol/atributo. Se agrega encabezado en negrita
 * con fondo oscuro (mismo tono que el header de la web, `--text-h` de
 * `index.css`), bordes en todas las celdas de datos, y
 * {@code autoSizeColumn} al final sobre las 6 columnas para que el ancho
 * se ajuste al contenido real en vez de quedar comprimido.
 *
 * <p><b>Chips de Titularidad (2026-08-26, pedido explícito del usuario:
 * "esa vista se plasme en pdf y excel"):</b> la celda de la columna
 * "Titularidad" (ROLES/GRUPOS/INBOX/DEFAULTINBOX) ahora usa el mismo
 * color por categoría que `TitularidadChips` en `ReporteTabla.tsx`
 * (`--cat-roles/grupos/inbox/default` de `index.css`) -- fondo claro +
 * texto en negrita del color de la categoría, en vez del estilo `dato`
 * neutro que compartía con el resto de columnas. El Excel ya mostraba
 * TODO expandido desde siempre (una fila por atributo, sin estado
 * colapsado que replicar) -- lo único que faltaba era el color.
 */
@ApplicationScoped
@Named("excel")
public class ExcelReportAdapter implements ReportGeneratorPort {

    private static final String[] ENCABEZADOS = {
            "Rol", "Tipo", "Aplicacion", "Titularidad", "AtributoCodigo", "AtributoNombre"
    };
    private static final int NUM_COLUMNAS = ENCABEZADOS.length;

    @Override
    public Uni<byte[]> generar(List<Reporte> reportes) {
        // Apache POI (XSSFWorkbook) no tiene API reactiva -- construir el
        // workbook y serializarlo son operaciones de CPU/E-S en memoria
        // bloqueantes por diseño de la librería. Se offloadea al worker
        // pool por defecto de Mutiny antes de tocar cualquier clase de
        // org.apache.poi.*, para no bloquear el hilo del event loop de
        // Vert.x (docs/architecture.md, regla dura).
        return Uni.createFrom().item(reportes)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transform(ExcelReportAdapter::generarExcel);
    }

    private static byte[] generarExcel(List<Reporte> reportes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet hoja = workbook.createSheet("Consolidado de Roles");
            Estilos estilos = new Estilos(workbook);

            int fila = 0;
            for (Reporte reporte : reportes) {
                fila = escribirReporte(hoja, estilos, fila, reporte);
            }

            ajustarAnchoColumnas(hoja);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            workbook.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Error generando el Excel del reporte", ex);
        }
    }

    /**
     * Ancho de columna por longitud del texto, en vez de
     * {@code XSSFSheet.autoSizeColumn}: este último mide el texto con
     * {@code java.awt.Font}/{@code TextLayout}, que necesita fontconfig y
     * fuentes del SO -- el contenedor Docker del backend no las tiene y
     * lanzaba {@code RuntimeException: Fontconfig head is null} (HTTP 500 en
     * {@code GET /api/reportes/{id}/descargar?formato=excel}). Las celdas
     * combinadas solo guardan el valor en su primera celda, así que el
     * recorrido por filas basta.
     */
    private static void ajustarAnchoColumnas(XSSFSheet hoja) {
        int[] maxCaracteres = new int[NUM_COLUMNAS];
        for (Row fila : hoja) {
            for (int col = 0; col < NUM_COLUMNAS; col++) {
                Cell celda = fila.getCell(col);
                if (celda != null && celda.getCellType() == CellType.STRING) {
                    maxCaracteres[col] = Math.max(maxCaracteres[col], celda.getStringCellValue().length());
                }
            }
        }
        for (int col = 0; col < NUM_COLUMNAS; col++) {
            // Unidades de 1/256 de carácter; piso de 3000 para que
            // "Tipo"/"Titularidad" no queden ilegibles y tope de 255
            // caracteres (límite de POI/Excel).
            int ancho = Math.min(maxCaracteres[col] + 2, 255) * 256;
            hoja.setColumnWidth(col, Math.max(ancho, 3000));
        }
    }

    private static int escribirReporte(XSSFSheet hoja, Estilos estilos, int filaInicial, Reporte reporte) {
        int fila = filaInicial;

        Row filaArea = hoja.createRow(fila++);
        crearCelda(filaArea, 0, "Area", estilos.etiqueta);
        crearCelda(filaArea, 1, reporte.areaCodigo() + " - " + reporte.areaNombre(), estilos.valor);

        Row filaCargo = hoja.createRow(fila++);
        crearCelda(filaCargo, 0, "Cargo", estilos.etiqueta);
        crearCelda(filaCargo, 1, reporte.cargoCodigo() + " - " + reporte.cargoNombre(), estilos.valor);

        fila++;

        Row filaEncabezado = hoja.createRow(fila++);
        for (int col = 0; col < ENCABEZADOS.length; col++) {
            crearCelda(filaEncabezado, col, ENCABEZADOS[col], estilos.encabezado);
        }

        for (DetalleRol detalle : reporte.detalleRol()) {
            int filaInicioDetalle = fila;
            fila = escribirBloqueAtributos(hoja, estilos, fila, CategoriaSuministro.ROLES,
                    detalle.suministroRoles());
            fila = escribirBloqueAtributos(hoja, estilos, fila, CategoriaSuministro.GRUPOS,
                    detalle.suministroGrupos());
            fila = escribirBloqueAtributos(hoja, estilos, fila, CategoriaSuministro.INBOX,
                    detalle.suministroInbox());
            fila = escribirBloqueAtributos(hoja, estilos, fila, CategoriaSuministro.DEFAULTINBOX,
                    detalle.suministroDefaultInbox());
            escribirRolUnaVez(hoja, estilos, filaInicioDetalle, fila - 1, detalle);
        }

        return fila + 1;
    }

    /**
     * Escribe solo las columnas de atributo (AtributoCodigo/AtributoNombre)
     * por fila -- Rol/Tipo/Aplicacion (columnas 0-2) se dejan sin valor
     * acá, {@link #escribirRolUnaVez} las completa una sola vez por bloque
     * de {@link DetalleRol} vía celda combinada. La categoría/Titularidad
     * (columna 3) tampoco se repite por fila -- se escribe una sola vez en
     * la primera fila de SU bloque y se combina hacia abajo (corrige el
     * mismo defecto de repetición que tenía Rol, reportado por el usuario
     * 2026-08-25: "en excel se repiten columnas repetidas" -- Titularidad
     * mostraba "ROLES"/"ROLES" o "GRUPOS" cuatro veces seguidas si esa
     * categoría tenía varios atributos).
     */
    private static int escribirBloqueAtributos(XSSFSheet hoja, Estilos estilos, int filaInicial,
            CategoriaSuministro categoria, List<Atributo> atributos) {
        if (atributos.isEmpty()) {
            return filaInicial;
        }
        int fila = filaInicial;
        int inicioCategoria = fila;
        for (Atributo atributo : atributos) {
            Row filaDato = hoja.createRow(fila++);
            crearCelda(filaDato, 0, "", estilos.dato);
            crearCelda(filaDato, 1, "", estilos.dato);
            crearCelda(filaDato, 2, "", estilos.dato);
            crearCelda(filaDato, 3, "", estilos.dato);
            crearCelda(filaDato, 4, atributo.atributoCodigo(), estilos.dato);
            crearCelda(filaDato, 5, atributo.atributoNombre(), estilos.dato);
        }
        int finCategoria = fila - 1;
        crearCelda(hoja.getRow(inicioCategoria), 3, categoria.name(), estilos.chip(categoria));
        if (finCategoria > inicioCategoria) {
            hoja.addMergedRegion(new CellRangeAddress(inicioCategoria, finCategoria, 3, 3));
        }
        return fila;
    }

    /**
     * Escribe Rol/Tipo/Aplicacion UNA sola vez por {@link DetalleRol}, en
     * una celda combinada que abarca todas sus filas de atributos
     * (`filaInicio`..`filaFin`) -- corrige bug real reportado por el
     * usuario 2026-08-25 ("como aprecias... [la columna Rol] esta muy
     * repetido, unifica en una columna"): antes se repetía el mismo
     * nombre de rol en CADA fila de atributo (podía ser 7+ veces seguidas
     * para un rol con muchos atributos), igual que hacía el legacy
     * (`ReporteManagedBean.excel()`) -- acá se corrige en vez de replicar
     * el defecto original. Si el detalle no generó ninguna fila (las 4
     * categorías vacías, caso raro pero posible), no hay dónde escribir
     * el rol y se omite silenciosamente -- mismo comportamiento que antes
     * (tampoco aparecía ninguna fila para ese detalle).
     */
    private static void escribirRolUnaVez(XSSFSheet hoja, Estilos estilos, int filaInicio, int filaFin,
            DetalleRol detalle) {
        if (filaFin < filaInicio) {
            return;
        }
        crearCelda(hoja.getRow(filaInicio), 0, detalle.rol(), estilos.dato);
        crearCelda(hoja.getRow(filaInicio), 1, detalle.tipo(), estilos.dato);
        crearCelda(hoja.getRow(filaInicio), 2, detalle.aplicacion(), estilos.dato);
        if (filaFin > filaInicio) {
            hoja.addMergedRegion(new CellRangeAddress(filaInicio, filaFin, 0, 0));
            hoja.addMergedRegion(new CellRangeAddress(filaInicio, filaFin, 1, 1));
            hoja.addMergedRegion(new CellRangeAddress(filaInicio, filaFin, 2, 2));
        }
    }

    private static void crearCelda(Row fila, int columna, String valor, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor == null ? "" : valor);
        celda.setCellStyle(estilo);
    }

    /** Estilos reutilizados por hoja -- POI recomienda crear un `CellStyle` por variante, no uno por celda. */
    private static final class Estilos {
        final CellStyle encabezado;
        final CellStyle etiqueta;
        final CellStyle valor;
        final CellStyle dato;
        private final CellStyle chipRoles;
        private final CellStyle chipGrupos;
        private final CellStyle chipInbox;
        private final CellStyle chipDefault;

        Estilos(XSSFWorkbook workbook) {
            Font fuenteEncabezado = workbook.createFont();
            fuenteEncabezado.setBold(true);
            fuenteEncabezado.setColor(IndexedColors.WHITE.getIndex());

            encabezado = workbook.createCellStyle();
            encabezado.setFont(fuenteEncabezado);
            ((XSSFCellStyle) encabezado).setFillForegroundColor(new XSSFColor(new Color(0x17, 0x1a, 0x21), null));
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            aplicarBordes(encabezado);

            Font fuenteEtiqueta = workbook.createFont();
            fuenteEtiqueta.setBold(true);
            etiqueta = workbook.createCellStyle();
            etiqueta.setFont(fuenteEtiqueta);
            etiqueta.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            etiqueta.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            aplicarBordes(etiqueta);

            valor = workbook.createCellStyle();
            aplicarBordes(valor);

            dato = workbook.createCellStyle();
            aplicarBordes(dato);
            // Centrado vertical: Rol/Tipo/Aplicacion ahora viven en una
            // celda combinada que puede abarcar varias filas de atributos
            // (ver `escribirRolUnaVez`) -- sin esto POI las alinea abajo
            // por default, dejando el nombre del rol pegado al borde
            // inferior de un bloque combinado alto.
            dato.setVerticalAlignment(VerticalAlignment.CENTER);

            chipRoles = estiloChip(workbook, new Color(0x2f, 0x5f, 0xd9), new Color(0xee, 0xf2, 0xfc));
            chipGrupos = estiloChip(workbook, new Color(0x7c, 0x3a, 0xed), new Color(0xf5, 0xef, 0xfe));
            chipInbox = estiloChip(workbook, new Color(0x0f, 0x94, 0x88), new Color(0xec, 0xf6, 0xf5));
            chipDefault = estiloChip(workbook, new Color(0xb4, 0x53, 0x09), new Color(0xf9, 0xf1, 0xeb));
        }

        /** Mismo tono de categoría que `--cat-roles/grupos/inbox/default` en `index.css` -- texto en negrita del color de la categoría sobre su fondo claro. */
        private static CellStyle estiloChip(XSSFWorkbook workbook, Color colorTexto, Color colorFondo) {
            Font fuente = workbook.createFont();
            fuente.setBold(true);
            ((XSSFFont) fuente).setColor(new XSSFColor(colorTexto, null));

            CellStyle estilo = workbook.createCellStyle();
            estilo.setFont(fuente);
            ((XSSFCellStyle) estilo).setFillForegroundColor(new XSSFColor(colorFondo, null));
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estilo.setAlignment(HorizontalAlignment.CENTER);
            estilo.setVerticalAlignment(VerticalAlignment.CENTER);
            aplicarBordes(estilo);
            return estilo;
        }

        /** Estilo de chip por categoría -- mismo mapeo que `CATEGORIAS` en `ReporteTabla.tsx`. */
        CellStyle chip(CategoriaSuministro categoria) {
            return switch (categoria) {
                case ROLES -> chipRoles;
                case GRUPOS -> chipGrupos;
                case INBOX -> chipInbox;
                case DEFAULTINBOX -> chipDefault;
            };
        }

        private static void aplicarBordes(CellStyle estilo) {
            estilo.setBorderTop(BorderStyle.THIN);
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBorderLeft(BorderStyle.THIN);
            estilo.setBorderRight(BorderStyle.THIN);
        }
    }
}
