package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Test de integración de {@code /api/estados} contra el LocalStack real de
 * Quarkus Dev Services (feature 3, {@code crud_catalogos_simples}).
 */
@QuarkusTest
class EstadoResourceTest {

    @Test
    void should_returnCreatedEstadoWithGeneratedCodigo_whenPostingNewEstado() {
        given()
                .contentType("application/json")
                .body("{\"nombre\":\"ACTIVO\"}")
                .when().post("/api/estados")
                .then()
                .statusCode(201)
                .body("codigo", notNullValue())
                .body("nombre", is("ACTIVO"));
    }

    @Test
    void should_findSameEstado_whenSearchingByCodigoAfterCreate() {
        String codigo = given()
                .contentType("application/json")
                .body("{\"nombre\":\"INACTIVO\"}")
                .when().post("/api/estados")
                .then()
                .statusCode(201)
                .extract().path("codigo").toString();

        given()
                .when().get("/api/estados/" + codigo)
                .then()
                .statusCode(200)
                .body("codigo", is(codigo))
                .body("nombre", is("INACTIVO"));
    }

    @Test
    void should_return404_whenEstadoCodeDoesNotExist() {
        given()
                .when().get("/api/estados/no-existe-999")
                .then()
                .statusCode(404);
    }
}
