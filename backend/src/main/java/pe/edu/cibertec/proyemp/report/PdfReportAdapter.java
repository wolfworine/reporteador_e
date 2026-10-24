package pe.edu.cibertec.proyemp.report;

import com.lowagie.text.Chunk;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import pe.edu.cibertec.proyemp.domain.Atributo;
import pe.edu.cibertec.proyemp.domain.CategoriaSuministro;
import pe.edu.cibertec.proyemp.domain.DetalleRol;
import pe.edu.cibertec.proyemp.domain.Reporte;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador PDF del {@link ReportGeneratorPort} usando OpenPDF (fork LGPL
 * 2.1 de iText 4/5, decisión documentada en {@code progress/current.md} y
 * {@code pom.xml} de esta feature).
 *
 * <p><b>Reescrito 2026-08-26 (pedido explícito del usuario: "esa vista se
 * plasme en pdf y excel... todo debe estar en expandido"):</b> refleja la
 * Dirección A del rediseño de la vista web (`ReporteTabla.tsx`,
 * `CargoSubTabla`/`TitularidadChips`) en vez de la tabla plana de 5
 * columnas de la versión anterior. Cada Rol es ahora un bloque -- una
 * línea de resumen (Rol/Tipo/Aplicación/Titularidad, con Titularidad como
 * chips de color, mismo criterio que
 * {@code --cat-roles/grupos/inbox/default} de `index.css`) seguida por su
 * lista de Atributos, siempre expandida -- un PDF no tiene estado de
 * interacción, así que "todo colapsado" no tendría forma de leerse.
 *
 * <p><b>Diagnóstico 2026-08-26, mismo día (pedido explícito del usuario:
 * "valida los pdfs a profundidad aun veo cuadro cortados hasta hojas
 * demas"):</b> la primera versión de este rediseño reproducía la grilla
 * de la web tal cual -- una columna por categoría (ROLES/GRUPOS/INBOX/
 * DEFAULTINBOX) puestas LADO A LADO dentro de una única fila de tabla.
 * Ese layout funciona en HTML (cada columna de un CSS grid fluye alto de
 * forma independiente) pero es frágil en un documento paginado: OpenPDF
 * calcula la altura de una fila de tabla como el MÁXIMO de sus celdas, y
 * si una categoría tiene muchos atributos (datos reales de este sistema
 * llegan a 60+ por Rol) esa fila termina siendo más alta que una página
 * completa -- ahí no hay forma de "empujar entera a la página siguiente"
 * (`setKeepTogether`, agregado en el ajuste anterior, no alcanza; y de
 * todos modos tampoco cabría en una página nueva), así que terminaba
 * partiéndose en un punto que no respeta el contenido de cada columna por
 * separado -- de ahí los cuadros cortados. Forzar cada bloque de Rol a
 * "todo o nada" con `setKeepTogether` además desperdiciaba el resto de la
 * página anterior cada vez que un bloque no entraba entero -- de ahí las
 * hojas de más.
 *
 * <p><b>Rediseño de la lista de Atributos:</b> se reemplaza la grilla de
 * columnas lado a lado por una lista APILADA (las 4 categorías una debajo
 * de la otra en vez de una al lado de la otra). Una lista apilada de
 * filas es el caso que {@code PdfPTable} sabe paginar de forma robusta de
 * fábrica -- corta siempre entre dos filas completas, nunca a mitad de
 * una, sin importar cuántas filas tenga una categoría ni en qué página
 * empiece.
 *
 * <p><b>Ajustado 2026-08-26, mismo día (pedido explícito del usuario: "por
 * que no se diseña como esta en la vista web... no se identifica bien la
 * titularidad"):</b> el primer intento de la lista apilada distinguía cada
 * tramo con una franja de color a la izquierda (mismo criterio que la
 * celda combinada de Titularidad en {@link ExcelReportAdapter}), sin texto
 * de categoría. El usuario prefirió que el PDF calque lo que la web
 * REALMENTE hace: `ReporteTabla.tsx#CategoriaAtributos` pone una caption
 * neutra ("ROLES"/"GRUPOS"/...) en cada caja, sin color -- el color en la
 * web vive únicamente en los chips de Titularidad de la línea de resumen
 * ({@link #chipsTitularidad}), nunca en la lista de atributos en sí. Se
 * revierte a eso: cada tramo lleva su caption de vuelta (misma caja clara
 * que {@link #celdaEncabezadoColumna}), sin franja de color, y cada
 * atributo vuelve a ser una sola celda "código - nombre" (no 2 columnas
 * separadas) -- más simple, y es lo que el usuario ya había confirmado
 * que prefería en una captura de un PDF generado antes del intento de la
 * franja de color.
 *
 * <p><b>Rediseño 2026-08-25 (pedido explícito del usuario, con captura de
 * la vista web real como referencia -- "la imagen asi deseo que este el
 * pdf"):</b> el usuario mostró una captura de `ReporteTabla.tsx` donde las
 * categorías con datos van TODAS lado a lado en una sola fila (no de a
 * pares con texto plano), cada una con su chip de color como caption Y con
 * 2 columnas reales por categoría ("Atributo código" / "Atributo nombre",
 * no una sola celda "código - nombre" concatenado). {@link
 * #tablaAtributosDelRol} vuelve a devolver una única tabla (no una lista
 * de pares) con {@code 2 × N} columnas (N = categorías con datos de ese
 * Rol), pero -- a diferencia del primer intento lado-a-lado que causó los
 * cuadros cortados -- cada índice de atributo es una FILA real de la tabla
 * compartida entre columnas, nunca una tabla anidada dentro de una celda.
 * Eso mantiene la propiedad de seguridad ya establecida: el alto de
 * cualquier fila queda acotado al de un único atributo (como mucho 2-3
 * líneas si el nombre es largo), sin importar que una categoría tenga 2
 * atributos y la de al lado 60 -- la categoría más corta simplemente
 * queda con celdas vacías debajo de su última fila mientras las demás
 * siguen, y {@code PdfPTable} sigue pudiendo partir entre filas sin
 * recortar nada.
 */
@ApplicationScoped
@Named("pdf")
public class PdfReportAdapter implements ReportGeneratorPort {

    private static final Color COLOR_TEXTO_H = new Color(0x17, 0x1a, 0x21);
    private static final Color COLOR_TEXTO_MUTED = new Color(0x6b, 0x72, 0x80);
    private static final Color COLOR_FONDO_CLARO = new Color(0xf4, 0xf5, 0xf7);
    private static final Color COLOR_BORDE = new Color(0xdc, 0xdf, 0xe4);
    /** Acento del texto "Atributo nombre" -- mismo azul de enlace que usa `ReporteTabla.tsx` para el nombre del atributo, igual en las 4 categorías (no varía por categoría, a diferencia del chip de caption). */
    private static final Color COLOR_NOMBRE_ATRIBUTO = new Color(0x2f, 0x5f, 0xd9);

    /** Mismos 4 tonos de categoría que `--cat-roles/grupos/inbox/default` en `index.css`. */
    private static final Color CAT_ROLES = new Color(0x2f, 0x5f, 0xd9);
    private static final Color CAT_ROLES_BG = new Color(0xee, 0xf2, 0xfc);
    private static final Color CAT_GRUPOS = new Color(0x7c, 0x3a, 0xed);
    private static final Color CAT_GRUPOS_BG = new Color(0xf5, 0xef, 0xfe);
    private static final Color CAT_INBOX = new Color(0x0f, 0x94, 0x88);
    private static final Color CAT_INBOX_BG = new Color(0xec, 0xf6, 0xf5);
    private static final Color CAT_DEFAULT = new Color(0xb4, 0x53, 0x09);
    private static final Color CAT_DEFAULT_BG = new Color(0xf9, 0xf1, 0xeb);

    private static final Font FUENTE_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
    private static final Font FUENTE_AREA = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_TEXTO_MUTED);
    private static final Font FUENTE_CARGO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_TEXTO_H);
    private static final Font FUENTE_ENCABEZADO_COL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, COLOR_TEXTO_MUTED);
    private static final Font FUENTE_ROL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_TEXTO_H);
    private static final Font FUENTE_CELDA = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private static final Font FUENTE_NOMBRE_ATRIBUTO = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_NOMBRE_ATRIBUTO);
    private static final Font FUENTE_CHIP_ROLES = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, CAT_ROLES);
    private static final Font FUENTE_CHIP_GRUPOS = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, CAT_GRUPOS);
    private static final Font FUENTE_CHIP_INBOX = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, CAT_INBOX);
    private static final Font FUENTE_CHIP_DEFAULT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, CAT_DEFAULT);

    @Override
    public Uni<byte[]> generar(List<Reporte> reportes) {
        // OpenPDF (com.lowagie.text.Document) no tiene API reactiva -- es
        // trabajo de E/S en memoria pero bloqueante por diseño de la
        // librería (escritura sincrónica al stream subyacente). Se
        // offloadea al worker pool por defecto de Mutiny antes de tocar
        // cualquier clase de com.lowagie.text.*, para no bloquear el hilo
        // del event loop de Vert.x (docs/architecture.md, regla dura).
        return Uni.createFrom().item(reportes)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transform(PdfReportAdapter::generarPdf);
    }

    private static byte[] generarPdf(List<Reporte> reportes) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4);
        Rectangle rect = new Rectangle(PageSize.LETTER.rotate());
        documento.setPageSize(rect);
        try {
            PdfWriter.getInstance(documento, salida).setInitialLeading(20);
            documento.setMargins(24, 24, 24, 24);
            documento.open();

            Paragraph titulo = new Paragraph("Consolidado de Roles", FUENTE_TITULO);
            titulo.setAlignment(Element.ALIGN_CENTER);
            titulo.setSpacingAfter(14);
            documento.add(titulo);

            for (Reporte reporte : reportes) {
                List<DetalleRol> roles = reporte.detalleRol();
                if (roles.isEmpty()) {
                    documento.add(tablaCabeceraCargo(reporte));
                    documento.add(tablaEncabezadoColumnas());
                } else {
                    // Cabecera + rótulos + el PRIMER Rol van en una sola
                    // tabla `keepTogether` -- ver el diagnóstico de
                    // "cabecera huérfana" en el Javadoc de la clase.
                    documento.add(tablaCabeceraConPrimerRol(reporte, roles.get(0)));
                    documento.add(tablaAtributosDelRol(roles.get(0)));
                    for (int i = 1; i < roles.size(); i++) {
                        documento.add(tablaResumenRol(roles.get(i)));
                        documento.add(tablaAtributosDelRol(roles.get(i)));
                    }
                }
                documento.add(new Paragraph(" ", FUENTE_CELDA));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Error generando el PDF del reporte", ex);
        } finally {
            if (documento.isOpen()) {
                documento.close();
            }
        }
        return salida.toByteArray();
    }

    /**
     * Punto único de creación de tablas "caja" pequeñas y de una sola fila
     * (cabecera de Cargo, fila de rótulos de columna, resumen de Rol) --
     * {@code setKeepTogether(true)} evita que OpenPDF corte la caja a la
     * mitad en un borde de página, empujándola entera a la siguiente en
     * vez de partirla. Deliberadamente NO se usa para
     * {@link #tablaAtributosDelRol} -- ver el diagnóstico en el Javadoc de
     * la clase de por qué esa lista necesita poder paginar fila por fila.
     */
    private static PdfPTable tablaBase(int columnas) {
        PdfPTable tabla = new PdfPTable(columnas);
        tabla.setKeepTogether(true);
        return tabla;
    }

    /** Encabezado de tarjeta -- espejo claro de `.cargo-card__header` (fondo claro, no la caja oscura de la versión anterior). Solo se usa sola en el caso raro de un Cargo sin ningún Rol; el caso normal va por {@link #tablaCabeceraConPrimerRol}. */
    private static PdfPTable tablaCabeceraCargo(Reporte reporte) throws DocumentException {
        PdfPTable tabla = tablaBase(1);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(6);
        tabla.addCell(celdaCabeceraCargo(reporte));
        return tabla;
    }

    /**
     * Cabecera de Cargo + fila de rótulos de columna + el resumen del
     * PRIMER Rol, las tres juntas en UNA tabla `keepTogether` -- bug real
     * 2026-08-26 visto por el usuario en capturas reales: la cabecera y
     * los rótulos son tablas separadas de {@link #tablaCabeceraCargo}/
     * {@link #tablaEncabezadoColumnas} (cada una `keepTogether` por su
     * cuenta), así que si ambas cabían al final de una página pero el
     * primer {@link #tablaResumenRol} no, ese resumen se empujaba solo a
     * la página siguiente -- la cabecera quedaba "huérfana", sin ningún
     * dato debajo, justo antes del borde de página (se ve como un cuadro
     * vacío/cortado). Uniendo las tres en una sola tabla, `keepTogether`
     * las mueve como unidad -- la cabecera de un Cargo nunca vuelve a
     * aparecer sin al menos su primer Rol.
     */
    private static PdfPTable tablaCabeceraConPrimerRol(Reporte reporte, DetalleRol primerRol) throws DocumentException {
        PdfPTable tabla = tablaBase(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[] {34, 14, 22, 30});
        tabla.setSpacingBefore(6);

        PdfPCell cabecera = celdaCabeceraCargo(reporte);
        cabecera.setColspan(4);
        tabla.addCell(cabecera);

        tabla.addCell(celdaEncabezadoColumna("Rol"));
        tabla.addCell(celdaEncabezadoColumna("Tipo"));
        tabla.addCell(celdaEncabezadoColumna("Aplicación"));
        tabla.addCell(celdaEncabezadoColumna("Titularidad"));

        agregarFilaResumenRol(tabla, primerRol);
        return tabla;
    }

    private static PdfPCell celdaCabeceraCargo(Reporte reporte) {
        Paragraph contenido = new Paragraph();
        contenido.add(new Chunk("Área: " + reporte.areaCodigo() + " - " + reporte.areaNombre() + "\n", FUENTE_AREA));
        contenido.add(new Chunk("Cargo: " + reporte.cargoCodigo() + " - " + reporte.cargoNombre(), FUENTE_CARGO));

        PdfPCell celda = new PdfPCell(contenido);
        celda.setBackgroundColor(COLOR_FONDO_CLARO);
        celda.setBorderColor(COLOR_BORDE);
        celda.setPadding(8);
        return celda;
    }

    /** Fila de rótulos de columna -- espejo de `.cargo-card__encabezado` (Rol/Tipo/Aplicación/Titularidad). Solo se usa sola en el caso raro de un Cargo sin ningún Rol; el caso normal va por {@link #tablaCabeceraConPrimerRol}. */
    private static PdfPTable tablaEncabezadoColumnas() throws DocumentException {
        PdfPTable tabla = tablaBase(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[] {34, 14, 22, 30});
        tabla.addCell(celdaEncabezadoColumna("Rol"));
        tabla.addCell(celdaEncabezadoColumna("Tipo"));
        tabla.addCell(celdaEncabezadoColumna("Aplicación"));
        tabla.addCell(celdaEncabezadoColumna("Titularidad"));
        return tabla;
    }

    /**
     * Línea de resumen de un Rol (2do en adelante dentro de su Cargo -- el
     * primero va dentro de {@link #tablaCabeceraConPrimerRol}) -- espejo
     * de `.rol-row__cabecera`: Rol/Tipo/Aplicación en texto simple (Tipo
     * nunca tuvo color propio en la web, solo Titularidad) y Titularidad
     * como chips de color.
     */
    private static PdfPTable tablaResumenRol(DetalleRol detalle) throws DocumentException {
        PdfPTable tabla = tablaBase(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[] {34, 14, 22, 30});
        agregarFilaResumenRol(tabla, detalle);
        return tabla;
    }

    private static void agregarFilaResumenRol(PdfPTable tabla, DetalleRol detalle) {
        tabla.addCell(celda(detalle.rol(), FUENTE_ROL));
        tabla.addCell(celda(detalle.tipo(), FUENTE_CELDA));
        tabla.addCell(celda(detalle.aplicacion(), FUENTE_CELDA));

        PdfPCell celdaTitularidad = new PdfPCell(chipsTitularidad(detalle));
        celdaTitularidad.setBorderColor(COLOR_BORDE);
        celdaTitularidad.setPadding(6);
        celdaTitularidad.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(celdaTitularidad);
    }

    /**
     * Chips de Titularidad -- un {@link Chunk} con fondo de color por
     * categoría (`Chunk#setBackground`, el equivalente de OpenPDF a un
     * `background` CSS inline) por cada categoría con datos, mismo orden y
     * mismo criterio que `TitularidadChips` en `ReporteTabla.tsx`.
     */
    private static Paragraph chipsTitularidad(DetalleRol detalle) {
        Paragraph parrafo = new Paragraph();
        parrafo.setAlignment(Element.ALIGN_CENTER);
        boolean primero = true;
        for (CategoriaSuministro categoria : detalle.tiposConDatos()) {
            if (!primero) {
                parrafo.add(new Chunk("  "));
            }
            primero = false;
            parrafo.add(chip(categoria.name(), fondoCategoria(categoria), fuenteChip(categoria)));
        }
        return parrafo;
    }

    /**
     * Tablas de Atributos del Rol -- las categorías con datos en PARES
     * lado a lado (2 columnas reales, una por categoría, alineadas fila a
     * fila), no apiladas en una sola columna angosta -- pedido explícito
     * del usuario 2026-08-26: "una tabla en cada uno... esta horrible
     * mucho espacio desperdiciado" (la versión apilada de un solo ancho
     * de columna dejaba la mitad derecha de la hoja en blanco). Cada
     * categoría lleva su propio chip de color como cabecera (mismo
     * criterio que {@link #chipsTitularidad}) en vez del texto plano.
     *
     * <p><b>Por qué en pares y no una grilla de hasta 4 columnas (que fue
     * la causa real de los cuadros cortados, ver el diagnóstico más
     * arriba en la clase):</b> acá cada FILA de la tabla es una fila de
     * datos real (una por atributo), no una categoría entera metida en
     * una sola celda -- la categoría más corta del par simplemente
     * termina antes (celdas vacías) mientras la más larga sigue. Como el
     * alto de cada fila es el de UNA línea de texto (nunca el de una
     * categoría completa), {@code PdfPTable} puede partir en cualquier
     * fila sin recortar nada -- la técnica estándar para evitar el
     * problema clásico de "el alto de una fila es el máximo de sus
     * celdas" cuando el contenido de las columnas tiene largo distinto
     * (ver la documentación de iText/OpenPDF sobre `PdfPTable`: por
     * defecto una fila que no entra se reintenta en una página nueva
     * antes de partirse, y solo se parte fila por fila, nunca a mitad de
     * una). Se devuelve una tabla por PAR de categorías (no una sola con
     * todo anidado) para no repetir el mismo riesgo de "celda con una
     * tabla anidada muy alta" que ya causó cuadros cortados antes -- cada
     * tabla del `List` se agrega directo al documento.
     */
    private static PdfPTable tablaAtributosDelRol(DetalleRol detalle) throws DocumentException {
        List<CategoriaSuministro> categorias = detalle.tiposConDatos();
        if (categorias.isEmpty()) {
            PdfPTable vacio = new PdfPTable(1);
            vacio.setWidthPercentage(100);
            vacio.setSpacingBefore(2);
            vacio.setSpacingAfter(6);
            PdfPCell celdaVacia = new PdfPCell(new Paragraph("Sin datos de suministro para este rol.", FUENTE_CELDA));
            celdaVacia.setBorderColor(COLOR_BORDE);
            celdaVacia.setPadding(6);
            vacio.addCell(celdaVacia);
            return vacio;
        }

        int n = categorias.size();
        PdfPTable tabla = new PdfPTable(n * 2);
        tabla.setWidthPercentage(100);
        float[] anchos = new float[n * 2];
        for (int i = 0; i < n; i++) {
            // 35:65 -- no 25:75 -- porque "Atributo código" no siempre es un
            // número corto: la categoría Inbox real usa un código de texto
            // completo (p.ej. "TESORERÍA INC", igual al nombre), que con
            // 25% partía en mitad de palabra ("TESORERÍ / A INC").
            anchos[i * 2] = 35f / n;
            anchos[i * 2 + 1] = 65f / n;
        }
        tabla.setWidths(anchos);
        tabla.setSpacingBefore(2);
        tabla.setSpacingAfter(6);

        for (CategoriaSuministro categoria : categorias) {
            PdfPCell chip = celdaChipCategoria(categoria);
            chip.setColspan(2);
            tabla.addCell(chip);
        }
        for (CategoriaSuministro categoria : categorias) {
            tabla.addCell(celdaEncabezadoColumna("Atributo código"));
            tabla.addCell(celdaEncabezadoColumna("Atributo nombre"));
        }

        List<List<Atributo>> listasPorCategoria = new ArrayList<>();
        int filas = 0;
        for (CategoriaSuministro categoria : categorias) {
            List<Atributo> atributos = atributosDeCategoria(detalle, categoria);
            listasPorCategoria.add(atributos);
            filas = Math.max(filas, atributos.size());
        }
        for (int f = 0; f < filas; f++) {
            for (List<Atributo> atributos : listasPorCategoria) {
                if (f < atributos.size()) {
                    Atributo atributo = atributos.get(f);
                    tabla.addCell(celda(atributo.atributoCodigo(), FUENTE_CELDA));
                    tabla.addCell(celda(atributo.atributoNombre(), FUENTE_NOMBRE_ATRIBUTO));
                } else {
                    tabla.addCell(celda("", FUENTE_CELDA));
                    tabla.addCell(celda("", FUENTE_CELDA));
                }
            }
        }
        return tabla;
    }

    /** Cabecera de columna de una categoría -- chip de color, mismo criterio que {@link #chipsTitularidad}; se agrega con {@code colspan=2} (código + nombre) en {@link #tablaAtributosDelRol}. */
    private static PdfPCell celdaChipCategoria(CategoriaSuministro categoria) {
        Paragraph parrafo = new Paragraph();
        parrafo.add(chip(categoria.name(), fondoCategoria(categoria), fuenteChip(categoria)));
        PdfPCell celda = new PdfPCell(parrafo);
        celda.setBorderColor(COLOR_BORDE);
        celda.setPadding(5);
        return celda;
    }

    /** Espejo de `detalle[key]` en `ReporteTabla.tsx` (`suministroRoles`/`suministroGrupos`/`suministroInbox`/`suministroDefaultinbox`). */
    private static List<Atributo> atributosDeCategoria(DetalleRol detalle, CategoriaSuministro categoria) {
        return switch (categoria) {
            case ROLES -> detalle.suministroRoles();
            case GRUPOS -> detalle.suministroGrupos();
            case INBOX -> detalle.suministroInbox();
            case DEFAULTINBOX -> detalle.suministroDefaultInbox();
        };
    }

    private static Color fondoCategoria(CategoriaSuministro categoria) {
        return switch (categoria) {
            case ROLES -> CAT_ROLES_BG;
            case GRUPOS -> CAT_GRUPOS_BG;
            case INBOX -> CAT_INBOX_BG;
            case DEFAULTINBOX -> CAT_DEFAULT_BG;
        };
    }

    private static Font fuenteChip(CategoriaSuministro categoria) {
        return switch (categoria) {
            case ROLES -> FUENTE_CHIP_ROLES;
            case GRUPOS -> FUENTE_CHIP_GRUPOS;
            case INBOX -> FUENTE_CHIP_INBOX;
            case DEFAULTINBOX -> FUENTE_CHIP_DEFAULT;
        };
    }

    private static Chunk chip(String texto, Color fondo, Font fuente) {
        Chunk chip = new Chunk(" " + texto + " ", fuente);
        chip.setBackground(fondo, 2, 2, 2, 2);
        return chip;
    }

    /** Celda de columna (Rol/Tipo/Aplicación) con texto simple. */
    private static PdfPCell celda(String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Paragraph(texto == null ? "" : texto, fuente));
        celda.setBorderColor(COLOR_BORDE);
        celda.setPadding(6);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }

    private static PdfPCell celdaEncabezadoColumna(String texto) {
        PdfPCell celda = new PdfPCell(new Paragraph(texto, FUENTE_ENCABEZADO_COL));
        celda.setBackgroundColor(COLOR_FONDO_CLARO);
        celda.setBorderColor(COLOR_BORDE);
        celda.setPadding(5);
        return celda;
    }
}
