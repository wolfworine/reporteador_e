package pe.edu.cibertec.proyemp.web.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import pe.edu.cibertec.proyemp.domain.CatalogoNoEncontradoException;

/**
 * Mapea las excepciones de dominio de los catálogos simples a respuestas
 * HTTP explícitas -- docs/conventions.md prohíbe un catch genérico que
 * trague la excepción y devuelva null/vacío en silencio.
 */
public class CatalogoExceptionMappers {

    @ServerExceptionMapper
    public Response mapCatalogoNoEncontrado(CatalogoNoEncontradoException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }

    @ServerExceptionMapper
    public Response mapValidacion(IllegalArgumentException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }

    public record ErrorResponse(String mensaje) {
    }
}
