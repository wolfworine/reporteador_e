package pe.edu.cibertec.proyemp.web.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import pe.edu.cibertec.proyemp.domain.RolNoEncontradoException;
import pe.edu.cibertec.proyemp.domain.SuministroNoEncontradoException;

/**
 * Mapea las excepciones de dominio de Rol/Suministro a respuestas HTTP
 * explícitas -- mismo criterio que
 * {@code web/exception/CatalogoExceptionMappers.java} de la feature 3
 * (docs/conventions.md prohíbe tragar la excepción en silencio). La
 * validación genérica ({@code IllegalArgumentException} -> 400) ya está
 * cubierta por {@code CatalogoExceptionMappers}, no se duplica aquí.
 */
public class RolExceptionMappers {

    @ServerExceptionMapper
    public Response mapRolNoEncontrado(RolNoEncontradoException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }

    @ServerExceptionMapper
    public Response mapSuministroNoEncontrado(SuministroNoEncontradoException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }

    public record ErrorResponse(String mensaje) {
    }
}
