package pe.edu.cibertec.proyemp.messaging;

/**
 * Payload de {@code report.requested} (feature 9). Serializado/deserializado
 * a JSON con Jackson ({@code ObjectMapper} CDI, ver
 * {@code messaging/ReporteRequestWorker}) sobre un canal Kafka de
 * {@code String} -- mismo criterio simple que el demo
 * {@code MyMessagingApplication} (canal {@code String}, no un
 * {@code Serde}/{@code Serializer} Kafka a medida por tipo).
 *
 * <p>{@code reporteId} es tanto el id de la {@code ReporteEjecucion}
 * persistida como la clave de partición Kafka del mensaje (ver
 * {@code docs/kafka-reactive-guidance.md}) -- garantiza que
 * {@code requested}->{@code ready}/{@code failed} de un mismo reporte se
 * procesen en orden.
 */
public record ReportRequestedEvent(String reporteId, String idAplicacion, String idArea, String formato) {
}
