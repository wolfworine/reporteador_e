package pe.edu.cibertec.proyemp.web.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import pe.edu.cibertec.proyemp.domain.ReporteEjecucionNoEncontradaException;
import pe.edu.cibertec.proyemp.domain.ReporteNoListoException;

/**
 * Mapea las excepciones de dominio de {@code web/ReporteResource} (feature
 * 10, {@code reportes_notificacion_sse_y_descarga}) a respuestas HTTP
 * explícitas -- mismo criterio que {@code RolExceptionMappers}/
 * {@code CatalogoExceptionMappers}: nada de tragar la excepción en silencio
 * (docs/conventions.md).
 *
 * <p>Las dos excepciones representan casos distintos (id inexistente vs. id
 * existente pero aún no {@code LISTO}) pero ambas se mapean al mismo 404 --
 * exactamente lo que pide el acceptance de {@code GET /api/reportes/{id}} y
 * {@code GET /api/reportes/{id}/descargar}: "404 si el id no existe o aún
 * no está LISTO".
 */
public class ReporteExceptionMappers {

    @ServerExceptionMapper
    public Response mapReporteEjecucionNoEncontrada(ReporteEjecucionNoEncontradaException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }

    @ServerExceptionMapper
    public Response mapReporteNoListo(ReporteNoListoException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }

    public record ErrorResponse(String mensaje) {
    }
}
