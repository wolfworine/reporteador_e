package pe.edu.cibertec.proyemp.messaging;

/**
 * Payload de {@code report.ready} (feature 9) -- publicado por
 * {@code messaging/ReporteRequestWorker} tras persistir
 * {@code estado=LISTO}. La feature 10 (SSE) consumirá este tópico para
 * notificar al frontend; el binario/JSON no viaja en el evento, se recupera
 * por {@code reporteId} vía {@code repository/ReporteEjecucionRepository}
 * (contrato ya persistido, ver {@code domain/ReporteEjecucion}).
 */
public record ReportReadyEvent(String reporteId) {
}
