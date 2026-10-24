package pe.edu.cibertec.proyemp.messaging;

/**
 * Evento SSE de la feature 10 ({@code reportes_notificacion_sse_y_descarga})
 * -- lo que viaja por {@code GET /api/reportes/eventos}
 * (@code Multi<ReporteEvento>}). Deliberadamente NO es el árbol completo
 * {@code List<Reporte>} (eso lo trae el cliente aparte con
 * {@code GET /api/reportes/{id}} si le interesa mostrar la tabla, ver
 * {@code web/ReporteResource}) -- solo lo mínimo para que el frontend sepa
 * qué reporte cambió de estado y reaccione (dejar de esperar, refrescar).
 *
 * <p>{@code estado} es un {@code String} propio ({@code "LISTO"}/
 * {@code "FALLIDO"}), no se reutiliza {@link pe.edu.cibertec.proyemp.domain.EstadoReporte}
 * directamente: ese enum tiene un tercer valor ({@code PENDIENTE}) que este
 * evento nunca emite (no tendría sentido notificar por SSE que algo sigue
 * pendiente) y usa el literal {@code ERROR} en vez de {@code FALLIDO}.
 * Los dos valores y el campo {@code mensaje} opcional se alinean a propósito
 * con {@code ../frontend/src/types/reporte.ts#ReporteEvento} (mismos 3
 * campos) para reducir fricción de integración, aunque el acceptance de
 * esta feature no exige coincidencia campo a campo para este endpoint en
 * particular (sí para {@code /{id}} y {@code /descargar}, ver
 * {@code progress/current.md}).
 */
public record ReporteEvento(String id, String estado, String mensaje) {

    public static final String ESTADO_LISTO = "LISTO";
    public static final String ESTADO_FALLIDO = "FALLIDO";

    public static ReporteEvento listo(String reporteId) {
        return new ReporteEvento(reporteId, ESTADO_LISTO, null);
    }

    public static ReporteEvento fallido(String reporteId, String motivo) {
        return new ReporteEvento(reporteId, ESTADO_FALLIDO, motivo);
    }
}
