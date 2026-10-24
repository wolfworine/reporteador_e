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
import pe.edu.cibertec.proyemp.domain.Suministro;
import pe.edu.cibertec.proyemp.service.SuministroService;
import pe.edu.cibertec.proyemp.web.dto.SuministroRequest;

/**
 * CRUD reactivo de Suministro bajo un Rol -- Patrón 2b del esquema
 * canónico ({@code SuministroRepository.findSuministro(idRol)}), paridad
 * funcional con {@code SuministroManagedBean} del legacy.
 */
@Path("/api/roles/{idRol}/suministros")
public class SuministroResource {

    private final SuministroService service;

    @Inject
    public SuministroResource(SuministroService service) {
        this.service = service;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<Suministro> listar(@PathParam("idRol") String idRol) {
        return service.listarPorRol(idRol);
    }

    @GET
    @Path("/{idSuministro}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Suministro> buscarPorId(@PathParam("idRol") String idRol,
            @PathParam("idSuministro") String idSuministro) {
        return service.buscarPorId(idRol, idSuministro);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> crear(@PathParam("idRol") String idRol, SuministroRequest request) {
        return service.crear(idRol, request.nombre(), request.prioridad())
                .onItem().transform(suministro -> Response.status(Response.Status.CREATED).entity(suministro).build());
    }

    @PUT
    @Path("/{idSuministro}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Suministro> actualizar(@PathParam("idRol") String idRol,
            @PathParam("idSuministro") String idSuministro, SuministroRequest request) {
        return service.actualizar(idRol, idSuministro, request.nombre(), request.prioridad());
    }

    @DELETE
    @Path("/{idSuministro}")
    public Uni<Response> eliminar(@PathParam("idRol") String idRol, @PathParam("idSuministro") String idSuministro) {
        return service.eliminar(idRol, idSuministro).onItem().transform(ignored -> Response.noContent().build());
    }
}
