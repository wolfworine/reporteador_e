package pe.edu.cibertec.proyemp.messaging;

/**
 * Payload de {@code report.failed} (feature 9) -- publicado por
 * {@code messaging/ReporteRequestWorker} cuando se agota el límite de
 * reintentos (poison pill, ver {@code docs/kafka-reactive-guidance.md}) y
 * se persiste {@code estado=ERROR}. La feature 10 (SSE) lo consumirá para
 * notificar el fallo al frontend en vez de dejarlo esperando
 * {@code report.ready} indefinidamente.
 */
public record ReportFailedEvent(String reporteId, String motivo) {
}
