package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Test de integración de {@code /api/cargos} contra el LocalStack real de
 * Quarkus Dev Services (feature 3, {@code crud_catalogos_simples}).
 */
@QuarkusTest
class CargoResourceTest {

    @Test
    void should_returnCreatedCargoWithGeneratedCodigo_whenPostingNewCargo() {
        given()
                .contentType("application/json")
                .body("{\"nombre\":\"ANALISTA\"}")
                .when().post("/api/cargos")
                .then()
                .statusCode(201)
                .body("codigo", notNullValue())
                .body("nombre", is("ANALISTA"));
    }

    @Test
    void should_findSameCargo_whenSearchingByCodigoAfterCreate() {
        String codigo = given()
                .contentType("application/json")
                .body("{\"nombre\":\"GERENTE\"}")
                .when().post("/api/cargos")
                .then()
                .statusCode(201)
                .extract().path("codigo").toString();

        given()
                .when().get("/api/cargos/" + codigo)
                .then()
                .statusCode(200)
                .body("codigo", is(codigo))
                .body("nombre", is("GERENTE"));
    }

    @Test
    void should_return404_whenCargoCodeDoesNotExist() {
        given()
                .when().get("/api/cargos/no-existe-999")
                .then()
                .statusCode(404);
    }
}
