package pe.edu.cibertec.proyemp.domain;

/**
 * Ejecución asíncrona de generación de un reporte (feature 9), identificada
 * por {@code id} (UUID, asignado por {@code web/ReporteGenerarResource} al
 * recibir {@code POST /api/reportes/generar}, antes de publicar
 * {@code report.requested} -- es también la clave de partición Kafka, ver
 * {@code docs/kafka-reactive-guidance.md}).
 *
 * <p>Persistida en la tabla única {@code RoleRepository}
 * ({@code PK=REPORTE#<id>}/{@code SK=METADATA}, ver
 * {@code repository/ReporteEjecucionRepository}) con el binario generado
 * (PDF/Excel) y la estructura {@code List<Reporte>} que lo originó
 * serializada a JSON ({@code reporteJson}) -- ambos recuperables por
 * {@code id} sin volver a ensamblar ni regenerar nada, contrato que la
 * feature 10 ({@code GET /api/reportes/{id}}, descarga, SSE) consume tal
 * cual.
 *
 * <p>{@code binario}/{@code reporteJson} son {@code null} mientras
 * {@code estado==PENDIENTE}; {@code motivoError} es {@code null} salvo que
 * {@code estado==ERROR}. {@code intentos} es el contador de reintentos del
 * worker (límite acotado ante mensajes envenenados, ver
 * {@code messaging/ReporteRequestWorker}).
 */
public record ReporteEjecucion(
        String id,
        EstadoReporte estado,
        String formato,
        String idAplicacion,
        String idArea,
        byte[] binario,
        String reporteJson,
        String motivoError,
        int intentos,
        String creadoEn,
        String actualizadoEn) {
}
