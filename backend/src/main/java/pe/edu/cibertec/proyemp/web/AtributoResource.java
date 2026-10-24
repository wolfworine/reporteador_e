package pe.edu.cibertec.proyemp.web;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pe.edu.cibertec.proyemp.domain.Atributo;
import pe.edu.cibertec.proyemp.service.AtributoService;
import pe.edu.cibertec.proyemp.web.dto.AtributoRequest;

/**
 * Consultas reactivas de Atributo (fila combinada Atributo+Detalle) --
 * patrones 2/3/4 del esquema canónico y las 4 categorías de suministro.
 * No expone CRUD completo (fuera del acceptance de esta feature): solo
 * {@code POST} para poblar datos de prueba y las 3 consultas de lectura.
 */
@Path("/api/atributos")
public class AtributoResource {

    private final AtributoService service;

    @Inject
    public AtributoResource(AtributoService service) {
        this.service = service;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> crear(AtributoRequest request) {
        return service.crear(request.aplicacionCodigo(), request.rolId(), request.suministroId(),
                        request.categoria(), request.atributoCodigo(), request.atributoNombre(), request.estado())
                .onItem().transform(atributo -> Response.status(Response.Status.CREATED).entity(atributo).build());
    }

    /**
     * {@code cargo} ausente replica {@code AtributoRepository.findAppArea}
     * (Patrones 2/3, solo GSI1PK); {@code cargo} presente replica
     * {@code AtributoRepository.findAppArea2} (Patrón 4,
     * {@code begins_with(GSI1SK,"CARGO#<cargo>")}) -- un solo endpoint
     * parametrizado por query param en vez de dos rutas, criterio de
     * ingeniería documentado en {@code progress/current.md}.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<Atributo> listar(@QueryParam("aplicacion") String aplicacion, @QueryParam("area") String area,
            @QueryParam("cargo") String cargo) {
        if (cargo == null || cargo.isBlank()) {
            return service.listarPorAplicacionArea(aplicacion, area);
        }
        return service.listarPorAplicacionAreaCargo(aplicacion, area, cargo);
    }

    /**
     * Las 4 categorías de suministro (Roles/Grupos/Inbox/DefaultInbox),
     * paridad con las 4 xhtml del legacy -- un solo endpoint parametrizado
     * por {@code categoria} en vez de 4 rutas, criterio de ingeniería
     * documentado en {@code progress/current.md}.
     */
    @GET
    @Path("/rol/{idRol}/categoria/{categoria}")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<Atributo> listarPorCategoria(@PathParam("idRol") String idRol,
            @PathParam("categoria") String categoria, @QueryParam("aplicacion") String aplicacion) {
        return service.listarPorCategoria(idRol, aplicacion, categoria);
    }
}
