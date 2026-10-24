package pe.edu.cibertec.proyemp.report;

import io.smallrye.mutiny.Uni;
import pe.edu.cibertec.proyemp.domain.Reporte;

import java.util.List;

/**
 * Único puerto que {@code service/} conoce para generar reportes binarios
 * (docs/architecture.md, "Reglas duras específicas de este proyecto":
 * "{@code report/ReportGeneratorPort} es la única interfaz que conoce
 * {@code service/} para generar reportes; iText/POI viven exclusivamente
 * detrás de sus adaptadores"). Ningún consumidor de este puerto importa
 * {@code com.lowagie.text.*} (OpenPDF, ver {@link PdfReportAdapter}) ni
 * {@code org.apache.poi.*} (ver {@link ExcelReportAdapter}) directamente.
 *
 * <p>Ambos formatos (PDF/Excel) implementan el mismo contrato -- un solo
 * método {@link #generar(List)} por adaptador, ya que cada adaptador
 * conoce su propio formato de salida. Esto permite que la feature 9
 * (orquestación Kafka) inyecte dos beans CDI del mismo tipo de puerto
 * (diferenciados con {@code @Named}, ver {@link PdfReportAdapter} y
 * {@link ExcelReportAdapter}) sin necesitar dos interfaces Java distintas.
 *
 * <p>Recibe directamente el {@code List<Reporte>} ensamblado por
 * {@code service/ReporteService} (feature 7) -- ningún adaptador vuelve a
 * tocar repositorios ni a deduplicar nada, la lista de entrada ya viene
 * sin duplicados por diseño de esa feature.
 */
public interface ReportGeneratorPort {

    /**
     * Genera el binario del reporte (PDF o Excel según el adaptador) para
     * la estructura Cargo→Rol→4 categorías de suministro. Nunca escribe a
     * disco -- el binario resultante vive solo en memoria
     * ({@code ByteArrayOutputStream}), listo para servirse por HTTP o
     * persistirse donde decida el llamador (fuera de scope de esta
     * feature).
     */
    Uni<byte[]> generar(List<Reporte> reportes);
}
