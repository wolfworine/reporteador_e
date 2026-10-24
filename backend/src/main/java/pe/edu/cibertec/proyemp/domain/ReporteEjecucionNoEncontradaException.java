package pe.edu.cibertec.proyemp.domain;

/**
 * Excepción de dominio: no existe ninguna {@link ReporteEjecucion} con el
 * {@code id} dado. En el flujo normal de la feature 9 esto no debería
 * ocurrir (el worker solo procesa ids que {@code web/ReporteGenerarResource}
 * ya persistió como {@code PENDIENTE} antes de publicar
 * {@code report.requested}) -- existe como fallo defensivo explícito en vez
 * de un {@code NullPointerException} silencioso, y la reutiliza la feature
 * 10 para su {@code GET /api/reportes/{id}} (mapeo a 404).
 */
public class ReporteEjecucionNoEncontradaException extends RuntimeException {

    private final String id;

    public ReporteEjecucionNoEncontradaException(String id) {
        super("No se encontro ejecucion de reporte con id '" + id + "'");
        this.id = id;
    }

    public String getReporteId() {
        return id;
    }
}
