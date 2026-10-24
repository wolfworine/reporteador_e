package pe.edu.cibertec.proyemp.web;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pe.edu.cibertec.proyemp.service.UsuarioService;
import pe.edu.cibertec.proyemp.web.dto.UsuarioRequest;
import pe.edu.cibertec.proyemp.web.dto.UsuarioResponse;

/**
 * CRUD reactivo (alta/lectura/baja, sin actualización -- ver Javadoc de
 * {@code service/UsuarioService}) de Usuario, paridad funcional con
 * {@code UsuarioManagedBean} del legacy. Todas las respuestas son
 * {@link UsuarioResponse}, nunca el dominio {@code Usuario} crudo -- ver
 * Javadoc de {@link UsuarioResponse}.
 */
@Path("/api/usuarios")
public class UsuarioResource {

    private final UsuarioService usuarioService;

    @Inject
    public UsuarioResource(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<UsuarioResponse> listar() {
        return usuarioService.listarTodos().onItem().transform(UsuarioResponse::de);
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UsuarioResponse> buscarPorId(@PathParam("id") String id) {
        return usuarioService.buscarPorId(id).onItem().transform(UsuarioResponse::de);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> crear(UsuarioRequest request) {
        return usuarioService.crear(request.nombres(), request.apellidos(), request.email(), request.password(),
                        request.tipo())
                .onItem().transform(usuario -> Response.status(Response.Status.CREATED)
                        .entity(UsuarioResponse.de(usuario))
                        .build());
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> eliminar(@PathParam("id") String id) {
        return usuarioService.eliminar(id).onItem().transform(ignored -> Response.noContent().build());
    }
}
