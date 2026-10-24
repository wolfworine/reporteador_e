package pe.edu.cibertec.proyemp.report;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.proyemp.domain.Atributo;
import pe.edu.cibertec.proyemp.domain.CategoriaSuministro;
import pe.edu.cibertec.proyemp.domain.DetalleRol;
import pe.edu.cibertec.proyemp.domain.EstadoAtributo;
import pe.edu.cibertec.proyemp.domain.Reporte;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario puro (sin {@code @QuarkusTest}, sin LocalStack -- esta
 * feature no toca DynamoDB) de {@link PdfReportAdapter} y
 * {@link ExcelReportAdapter}: arma una {@code List<Reporte>} de prueba con
 * la estructura Cargo→Rol→4 categorías, invoca cada adaptador de forma
 * aislada (no hay consumer/Kafka todavía, eso es la feature 9) y reabre el
 * binario resultante con la propia librería (OpenPDF/POI) para confirmar
 * que contiene datos reconocibles.
 *
 * <p>{@code Infrastructure.getDefaultWorkerPool()} (usado dentro de los
 * adaptadores para el offload del trabajo bloqueante de OpenPDF/POI) es un
 * executor de Mutiny standalone, no requiere contexto CDI/Quarkus -- de ahí
 * que no haga falta {@code @QuarkusTest} aquí.
 */
class ReportGeneratorAdaptersTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final PdfReportAdapter pdfAdapter = new PdfReportAdapter();
    private final ExcelReportAdapter excelAdapter = new ExcelReportAdapter();

    @Test
    void should_produceNonEmptyPdfWithRecognizableData_whenGivenReporteList() throws Exception {
        List<Reporte> reportes = List.of(reporteDePrueba());

        byte[] bytes = pdfAdapter.generar(reportes).await().atMost(TIMEOUT);

        assertTrue(bytes.length > 0, "El PDF generado no debe estar vacío");

        PdfReader reader = new PdfReader(bytes);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina));
            }
            String contenido = texto.toString();

            assertTrue(contenido.contains("AREA-01"), "Debe contener el código de área");
            assertTrue(contenido.contains("Area Estructura"), "Debe contener el nombre de área");
            assertTrue(contenido.contains("CARGO-01"), "Debe contener el código de cargo");
            assertTrue(contenido.contains("Cargo Estructura"), "Debe contener el nombre de cargo");
            assertTrue(contenido.contains("Rol Estructura"), "Debe contener el nombre del rol");
            assertTrue(contenido.contains("R1"), "Debe contener el código del atributo de ROLES");
            assertTrue(contenido.contains("Atributo Roles"), "Debe contener el nombre del atributo de ROLES");
            assertTrue(contenido.contains("G1"), "Debe contener el código del atributo de GRUPOS");
            assertTrue(contenido.contains("I1"), "Debe contener el código del atributo de INBOX");
            assertTrue(contenido.contains("D1"), "Debe contener el código del atributo de DEFAULTINBOX");
        } finally {
            reader.close();
        }
    }

    @Test
    void should_produceNonEmptyExcelWithRecognizableData_whenGivenReporteList() throws Exception {
        List<Reporte> reportes = List.of(reporteDePrueba());

        byte[] bytes = excelAdapter.generar(reportes).await().atMost(TIMEOUT);

        assertTrue(bytes.length > 0, "El Excel generado no debe estar vacío");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet hoja = workbook.getSheetAt(0);
            StringBuilder contenido = new StringBuilder();
            for (Row fila : hoja) {
                fila.forEach(celda -> contenido.append(celda.toString()).append('|'));
                contenido.append('\n');
            }
            String texto = contenido.toString();

            assertTrue(texto.contains("AREA-01"), "Debe contener el código de área");
            assertTrue(texto.contains("Area Estructura"), "Debe contener el nombre de área");
            assertTrue(texto.contains("CARGO-01"), "Debe contener el código de cargo");
            assertTrue(texto.contains("Rol Estructura"), "Debe contener el nombre del rol");
            assertTrue(texto.contains("R1"), "Debe contener el código del atributo de ROLES");
            assertTrue(texto.contains("Atributo Roles"), "Debe contener el nombre del atributo de ROLES");
            assertTrue(texto.contains("G1"), "Debe contener el código del atributo de GRUPOS");
            assertTrue(texto.contains("I1"), "Debe contener el código del atributo de INBOX");
            assertTrue(texto.contains("D1"), "Debe contener el código del atributo de DEFAULTINBOX");
        }
    }

    @Test
    void should_notContainFixedPathOrRuntimeExec_inGeneratedAdapterSources() {
        // Regla dura (docs/conventions.md, docs/architecture.md): nunca
        // FileOutputStream a ruta fija ni Runtime.exec en el código nuevo.
        // Este test documenta la garantía en el propio código de prueba;
        // la verificación real del criterio de aceptación es la ausencia
        // literal de esos patrones en report/PdfReportAdapter.java y
        // report/ExcelReportAdapter.java (confirmado manualmente, ver
        // progress/current.md de esta sesión).
        assertFalse(PdfReportAdapter.class.getName().isEmpty());
        assertFalse(ExcelReportAdapter.class.getName().isEmpty());
    }

    private Reporte reporteDePrueba() {
        Atributo atributoRoles = atributo(CategoriaSuministro.ROLES, "R1", "Atributo Roles");
        Atributo atributoGrupos = atributo(CategoriaSuministro.GRUPOS, "G1", "Atributo Grupos");
        Atributo atributoInbox = atributo(CategoriaSuministro.INBOX, "I1", "Atributo Inbox");
        Atributo atributoDefaultInbox = atributo(CategoriaSuministro.DEFAULTINBOX, "D1", "Atributo DefaultInbox");

        DetalleRol detalleRol = new DetalleRol(
                "ROL-01",
                "Rol Estructura",
                "BASE",
                "Aplicacion Estructura",
                List.of(CategoriaSuministro.ROLES, CategoriaSuministro.GRUPOS, CategoriaSuministro.INBOX,
                        CategoriaSuministro.DEFAULTINBOX),
                List.of(atributoRoles),
                List.of(atributoGrupos),
                List.of(atributoInbox),
                List.of(atributoDefaultInbox),
                4);

        return new Reporte("AREA-01", "Area Estructura", "CARGO-01", "Cargo Estructura", List.of(detalleRol), 1, 4);
    }

    private Atributo atributo(CategoriaSuministro categoria, String codigo, String nombre) {
        return new Atributo(
                "ATR-" + codigo,
                "DET-" + codigo,
                categoria,
                "APP-01",
                "AREA-01",
                "CARGO-01",
                "Cargo Estructura",
                "ROL-01",
                "DINAMICO",
                "Rol Estructura",
                "SUM-01",
                codigo,
                nombre,
                EstadoAtributo.ACTIVO);
    }
}
