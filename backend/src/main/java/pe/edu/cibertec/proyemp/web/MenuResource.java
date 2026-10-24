package pe.edu.cibertec.proyemp.web;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pe.edu.cibertec.proyemp.domain.MenuItem;
import pe.edu.cibertec.proyemp.domain.MenuNodo;
import pe.edu.cibertec.proyemp.domain.TipoUsuario;
import pe.edu.cibertec.proyemp.service.MenuService;
import pe.edu.cibertec.proyemp.web.dto.MenuRequest;

import java.util.List;
import java.util.Locale;

/**
 * {@code GET /api/menu/{tipoUsuario}} -- reemplaza {@code MenuController.java}
 * del legacy, devolviendo el árbol de menú (submenús con sus ítems hijos
 * reales) filtrado por {@code tipoUsuario} y estado activo. Ver Javadoc de
 * {@link MenuService} para el detalle de las dos correcciones de bug de
 * origen respecto al legacy.
 *
 * <p>Un {@code tipoUsuario} sin ningún menú configurado no es un 404 -- no
 * es un recurso identificable individualmente, es simplemente una lista
 * vacía (mismo criterio que las 4 categorías de suministro de la feature 4,
 * ver {@code AtributoResource}).
 */
@Path("/api/menu")
public class MenuResource {

    private final MenuService menuService;

    @Inject
    public MenuResource(MenuService menuService) {
        this.menuService = menuService;
    }

    @GET
    @Path("/{tipoUsuario}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<MenuNodo>> obtenerMenu(@PathParam("tipoUsuario") String tipoUsuario) {
        return menuService.obtenerArbol(parseTipoUsuario(tipoUsuario));
    }

    /**
     * Alta de un submenú o ítem de Menu, solo para poblar datos de prueba
     * -- ningún acceptance criterion de esta feature exige un endpoint de
     * escritura, pero los tests de integración necesitan una forma de crear
     * el escenario (submenús/ítems por tipoUsuario) sin acceder a DynamoDB
     * directamente desde el test, mismo precedente que
     * {@code POST /api/atributos} de la feature 4.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> crear(MenuRequest request) {
        MenuItem datos = request.aDominio();
        return menuService.crear(datos)
                .onItem().transform(item -> Response.status(Response.Status.CREATED).entity(item).build());
    }

    private TipoUsuario parseTipoUsuario(String tipoUsuario) {
        try {
            return TipoUsuario.valueOf(tipoUsuario == null ? null : tipoUsuario.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException(
                    "tipoUsuario invalido '" + tipoUsuario + "', valores validos: ADMINISTRADOR, OPERADOR");
        }
    }
}
