package pe.edu.cibertec.proyemp.domain;

/**
 * Estado de una ejecución de generación de reporte asíncrona (feature 9,
 * {@code reportes_generacion_async_kafka}). Persistido junto al resultado
 * en DynamoDB (ver {@link ReporteEjecucion} y
 * {@code repository/ReporteEjecucionRepository}), es el "dedupe key"
 * natural que el worker Kafka usa para no regenerar un reporte ante una
 * redelivery de {@code report.requested} (docs/kafka-reactive-guidance.md).
 */
public enum EstadoReporte {
    PENDIENTE,
    LISTO,
    ERROR
}
