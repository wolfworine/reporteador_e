package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Test de integración de {@code /api/ubicaciones} contra el LocalStack real
 * de Quarkus Dev Services (feature 3, {@code crud_catalogos_simples}).
 */
@QuarkusTest
class UbicacionResourceTest {

    @Test
    void should_returnCreatedUbicacionWithGeneratedCodigo_whenPostingNewUbicacion() {
        given()
                .contentType("application/json")
                .body("{\"nombre\":\"LIMA - SEDE CENTRAL\"}")
                .when().post("/api/ubicaciones")
                .then()
                .statusCode(201)
                .body("codigo", notNullValue())
                .body("nombre", is("LIMA - SEDE CENTRAL"));
    }

    @Test
    void should_findSameUbicacion_whenSearchingByCodigoAfterCreate() {
        String codigo = given()
                .contentType("application/json")
                .body("{\"nombre\":\"AREQUIPA\"}")
                .when().post("/api/ubicaciones")
                .then()
                .statusCode(201)
                .extract().path("codigo").toString();

        given()
                .when().get("/api/ubicaciones/" + codigo)
                .then()
                .statusCode(200)
                .body("codigo", is(codigo))
                .body("nombre", is("AREQUIPA"));
    }

    @Test
    void should_return404_whenUbicacionCodeDoesNotExist() {
        given()
                .when().get("/api/ubicaciones/no-existe-999")
                .then()
                .statusCode(404);
    }
}
