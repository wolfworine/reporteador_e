package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Test de integración de {@code /api/areas} contra el LocalStack real que
 * Quarkus Dev Services autoprovisiona para {@code @QuarkusTest} (feature 3
 * de {@code feature_list.json}, {@code crud_catalogos_simples}) -- no usa
 * mocks de DynamoDB.
 */
@QuarkusTest
class AreaResourceTest {

    @Test
    void should_returnCreatedAreaWithGeneratedCodigo_whenPostingNewArea() {
        given()
                .contentType("application/json")
                .body("{\"nombre\":\"ARQUITECTURA EMPRESARIAL\"}")
                .when().post("/api/areas")
                .then()
                .statusCode(201)
                .body("codigo", notNullValue())
                .body("nombre", is("ARQUITECTURA EMPRESARIAL"));
    }

    @Test
    void should_findSameArea_whenSearchingByCodigoAfterCreate() {
        String codigo = given()
                .contentType("application/json")
                .body("{\"nombre\":\"TECNOLOGIA\"}")
                .when().post("/api/areas")
                .then()
                .statusCode(201)
                .extract().path("codigo").toString();

        given()
                .when().get("/api/areas/" + codigo)
                .then()
                .statusCode(200)
                .body("codigo", is(codigo))
                .body("nombre", is("TECNOLOGIA"));
    }

    @Test
    void should_updateAreaName_whenPuttingExistingCodigo() {
        String codigo = given()
                .contentType("application/json")
                .body("{\"nombre\":\"NOMBRE ORIGINAL\"}")
                .when().post("/api/areas")
                .then()
                .statusCode(201)
                .extract().path("codigo").toString();

        given()
                .contentType("application/json")
                .body("{\"nombre\":\"NOMBRE ACTUALIZADO\"}")
                .when().put("/api/areas/" + codigo)
                .then()
                .statusCode(200)
                .body("nombre", is("NOMBRE ACTUALIZADO"));

        given()
                .when().get("/api/areas/" + codigo)
                .then()
                .statusCode(200)
                .body("nombre", is("NOMBRE ACTUALIZADO"));
    }

    @Test
    void should_return404_whenAreaCodeDoesNotExist() {
        given()
                .when().get("/api/areas/no-existe-999")
                .then()
                .statusCode(404);
    }

    @Test
    void should_return404_whenUpdatingAreaThatDoesNotExist() {
        given()
                .contentType("application/json")
                .body("{\"nombre\":\"CUALQUIERA\"}")
                .when().put("/api/areas/no-existe-999")
                .then()
                .statusCode(404);
    }

    @Test
    void should_return400_whenCreatingAreaWithBlankName() {
        given()
                .contentType("application/json")
                .body("{\"nombre\":\"\"}")
                .when().post("/api/areas")
                .then()
                .statusCode(400);
    }

    @Test
    void should_listAtLeastOneArea_whenAreasWereCreatedBefore() {
        given()
                .contentType("application/json")
                .body("{\"nombre\":\"AREA PARA LISTAR\"}")
                .when().post("/api/areas")
                .then()
                .statusCode(201);

        given()
                .when().get("/api/areas")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0));
    }

    @Test
    void should_return404_whenGettingDeletedArea() {
        String codigo = given()
                .contentType("application/json")
                .body("{\"nombre\":\"AREA A ELIMINAR\"}")
                .when().post("/api/areas")
                .then()
                .statusCode(201)
                .extract().path("codigo").toString();

        given()
                .when().delete("/api/areas/" + codigo)
                .then()
                .statusCode(204);

        given()
                .when().get("/api/areas/" + codigo)
                .then()
                .statusCode(404);
    }
}
