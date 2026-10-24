package pe.edu.cibertec.proyemp.web.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import pe.edu.cibertec.proyemp.domain.CredencialesInvalidasException;
import pe.edu.cibertec.proyemp.domain.EmailDuplicadoException;
import pe.edu.cibertec.proyemp.domain.UsuarioNoEncontradoException;

/**
 * Mapea las excepciones de dominio de Usuario/Auth a respuestas HTTP
 * explícitas -- mismo criterio que
 * {@code web/exception/RolExceptionMappers.java}/{@code CatalogoExceptionMappers.java}
 * (docs/conventions.md prohíbe tragar la excepción en silencio).
 */
public class UsuarioExceptionMappers {

    @ServerExceptionMapper
    public Response mapUsuarioNoEncontrado(UsuarioNoEncontradoException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }

    @ServerExceptionMapper
    public Response mapEmailDuplicado(EmailDuplicadoException exception) {
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }

    @ServerExceptionMapper
    public Response mapCredencialesInvalidas(CredencialesInvalidasException exception) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }

    public record ErrorResponse(String mensaje) {
    }
}
