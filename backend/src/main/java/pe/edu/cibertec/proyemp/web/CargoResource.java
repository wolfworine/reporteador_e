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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pe.edu.cibertec.proyemp.domain.Cargo;
import pe.edu.cibertec.proyemp.service.CargoService;
import pe.edu.cibertec.proyemp.web.dto.CatalogoRequest;

/**
 * CRUD reactivo de Cargo -- paridad funcional con {@code CargoManagedBean}
 * del legacy.
 */
@Path("/api/cargos")
public class CargoResource {

    private final CargoService service;

    @Inject
    public CargoResource(CargoService service) {
        this.service = service;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<Cargo> listar() {
        return service.listarTodos();
    }

    @GET
    @Path("/{codigo}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Cargo> buscarPorCodigo(@PathParam("codigo") String codigo) {
        return service.buscarPorCodigo(codigo);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> crear(CatalogoRequest request) {
        return service.crear(request.nombre())
                .onItem().transform(cargo -> Response.status(Response.Status.CREATED).entity(cargo).build());
    }

    @PUT
    @Path("/{codigo}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Cargo> actualizar(@PathParam("codigo") String codigo, CatalogoRequest request) {
        return service.actualizar(codigo, request.nombre());
    }

    @DELETE
    @Path("/{codigo}")
    public Uni<Response> eliminar(@PathParam("codigo") String codigo) {
        return service.eliminar(codigo).onItem().transform(ignored -> Response.noContent().build());
    }
}
