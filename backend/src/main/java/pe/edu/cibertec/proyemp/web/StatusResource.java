package pe.edu.cibertec.proyemp.web;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Recurso mínimo sin lógica de dominio, usado en el bootstrap del proyecto
 * (feature 1 de feature_list.json) para probar que el modelo reactivo
 * (SmallRye Mutiny, Uni&lt;Response&gt;) compila y responde de punta a
 * punta a través de la capa {@code web/} definida en
 * docs/conventions.md. No representa ninguna entidad del dominio legacy.
 */
@Path("/api/status")
public class StatusResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> status() {
        return Uni.createFrom()
                .item(new StatusPayload("UP", "entel-role-repository-backend"))
                .onItem().transform(payload -> Response.ok(payload).build());
    }

    public record StatusPayload(String status, String service) {
    }
}
