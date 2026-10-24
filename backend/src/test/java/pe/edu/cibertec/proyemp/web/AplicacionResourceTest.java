package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Test de integración de {@code /api/aplicaciones} contra el LocalStack
 * real de Quarkus Dev Services (feature 3, {@code crud_catalogos_simples}).
 */
@QuarkusTest
class AplicacionResourceTest {

    @Test
    void should_returnCreatedAplicacionWithGeneratedCodigo_whenPostingNewAplicacion() {
        given()
                .contentType("application/json")
                .body("{\"nombre\":\"PORTAL\"}")
                .when().post("/api/aplicaciones")
                .then()
                .statusCode(201)
                .body("codigo", notNullValue())
                .body("nombre", is("PORTAL"));
    }

    @Test
    void should_findSameAplicacion_whenSearchingByCodigoAfterCreate() {
        String codigo = given()
                .contentType("application/json")
                .body("{\"nombre\":\"SAP\"}")
                .when().post("/api/aplicaciones")
                .then()
                .statusCode(201)
                .extract().path("codigo").toString();

        given()
                .when().get("/api/aplicaciones/" + codigo)
                .then()
                .statusCode(200)
                .body("codigo", is(codigo))
                .body("nombre", is("SAP"));
    }

    @Test
    void should_return404_whenAplicacionCodeDoesNotExist() {
        given()
                .when().get("/api/aplicaciones/no-existe-999")
                .then()
                .statusCode(404);
    }
}
