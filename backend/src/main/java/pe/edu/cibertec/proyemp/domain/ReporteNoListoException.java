package pe.edu.cibertec.proyemp.domain;

/**
 * Excepción de dominio: existe una {@link ReporteEjecucion} con el {@code id}
 * dado, pero su {@code estado} todavía no es {@code LISTO} (sigue
 * {@code PENDIENTE} o terminó en {@code ERROR}). Distinta de
 * {@link ReporteEjecucionNoEncontradaException} (el id ni siquiera existe)
 * aunque ambas se mapean al mismo código HTTP 404 en
 * {@code web/exception/ReporteExceptionMappers} -- feature 10
 * ({@code reportes_notificacion_sse_y_descarga}), acceptance: "404 si el id
 * no existe o aún no está LISTO" para {@code GET /api/reportes/{id}} y
 * {@code GET /api/reportes/{id}/descargar}.
 */
public class ReporteNoListoException extends RuntimeException {

    private final String id;
    private final EstadoReporte estadoActual;

    public ReporteNoListoException(String id, EstadoReporte estadoActual) {
        super("El reporte con id '" + id + "' aun no esta LISTO (estado actual: " + estadoActual + ")");
        this.id = id;
        this.estadoActual = estadoActual;
    }

    public String getReporteId() {
        return id;
    }

    public EstadoReporte getEstadoActual() {
        return estadoActual;
    }
}
