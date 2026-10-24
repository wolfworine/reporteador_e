package pe.edu.cibertec.proyemp.web;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pe.edu.cibertec.proyemp.domain.CargoConRoles;
import pe.edu.cibertec.proyemp.domain.Rol;
import pe.edu.cibertec.proyemp.service.AtributoService;
import pe.edu.cibertec.proyemp.service.RolService;
import pe.edu.cibertec.proyemp.web.dto.RolRequest;

import java.util.List;

/**
 * CRUD reactivo de Rol -- paridad funcional con {@code RolManagedBean} del
 * legacy, más el endpoint agrupado que replica
 * {@code CargoRepository.findCargo(aplicacion, area)} (acceptance criterio
 * 1 de la feature 4: forma de respuesta = lista de Cargos con sus Roles,
 * ver {@link CargoConRoles} para la justificación).
 */
@Path("/api/roles")
public class RolResource {

    private final RolService rolService;
    private final AtributoService atributoService;

    @Inject
    public RolResource(RolService rolService, AtributoService atributoService) {
        this.rolService = rolService;
        this.atributoService = atributoService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<Rol> listar() {
        return rolService.listarTodos();
    }

    /**
     * Replica {@code CargoRepository.findCargo(aplicacion, area)}. Path
     * literal antes de {@code /{id}} -- JAX-RS resuelve el segmento
     * literal con prioridad sobre el patrón de path param.
     */
    @GET
    @Path("/cargos")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<CargoConRoles>> cargosConRoles(@QueryParam("aplicacion") String aplicacion,
            @QueryParam("area") String area) {
        return atributoService.listarCargosConRoles(aplicacion, area);
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Rol> buscarPorId(@PathParam("id") String id) {
        return rolService.buscarPorId(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> crear(RolRequest request) {
        return rolService.crear(request.aDominio())
                .onItem().transform(rol -> Response.status(Response.Status.CREATED).entity(rol).build());
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Rol> actualizar(@PathParam("id") String id, RolRequest request) {
        return rolService.actualizar(id, request.aDominio());
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> eliminar(@PathParam("id") String id) {
        return rolService.eliminar(id).onItem().transform(ignored -> Response.noContent().build());
    }
}
