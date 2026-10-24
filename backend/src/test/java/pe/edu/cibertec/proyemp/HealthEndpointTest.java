package pe.edu.cibertec.proyemp;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Smoke test de la feature 1 (bootstrap_quarkus_reactive_project): confirma
 * que la extensión smallrye-health (agregada además de las 4 pedidas
 * originalmente en feature_list.json, ver progress/history.md) expone
 * GET /q/health y responde 200.
 */
@QuarkusTest
class HealthEndpointTest {

    @Test
    void should_return200_whenHealthEndpointIsCalled() {
        given()
          .when().get("/q/health")
          .then()
             .statusCode(200);
    }
}
