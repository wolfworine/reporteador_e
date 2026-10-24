package pe.edu.cibertec.proyemp.web;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import pe.edu.cibertec.proyemp.service.AuthService;
import pe.edu.cibertec.proyemp.web.dto.LoginRequest;
import pe.edu.cibertec.proyemp.web.dto.UsuarioResponse;

/**
 * Reemplazo reactivo de {@code LoginManagedBean.iniciarSesion()}. Endpoint
 * mínimo a propósito: recibe email+password, valida contra el hash BCrypt
 * guardado, y devuelve datos básicos del usuario si es válido (200) o 401 si
 * no. El legacy no tenía noción de token/JWT más allá de la sesión JSF
 * (`FacesContext...getSessionMap().put("usuario", us)`) -- este endpoint
 * deliberadamente NO inventa un mecanismo de sesión/JWT propio; eso es una
 * decisión de diseño explícita de esta feature, documentada en
 * {@code progress/current.md}, y puede ser una feature futura no listada
 * todavía en {@code feature_list.json} si el frontend lo necesita.
 */
@Path("/api/auth")
public class AuthResource {

    private final AuthService authService;

    @Inject
    public AuthResource(AuthService authService) {
        this.authService = authService;
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UsuarioResponse> login(LoginRequest request) {
        return authService.login(request.email(), request.password()).onItem().transform(UsuarioResponse::de);
    }
}
