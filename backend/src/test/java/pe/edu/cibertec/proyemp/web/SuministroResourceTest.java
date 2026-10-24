package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test de integración de {@code /api/roles/{idRol}/suministros} contra el
 * LocalStack real de Quarkus Dev Services -- Patrón 2b del esquema
 * canónico ({@code SuministroRepository.findSuministro(idRol)}: Query por
 * PK con {@code begins_with(SK,"SUMINISTRO#")}).
 */
@QuarkusTest
class SuministroResourceTest {

    @Test
    void should_returnCreatedSuministroWithGeneratedId_whenPostingUnderExistingRol() {
        String idRol = RolResourceTest.crearRolCompleto("AREA-SUM-1", "CARGO-SUM-1", "ROL-SUM-1");

        given()
                .contentType("application/json")
                .body("{\"nombre\":\"CORREO\",\"prioridad\":\"ALTA\"}")
                .when().post("/api/roles/" + idRol + "/suministros")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("rolId", is(idRol))
                .body("nombre", is("CORREO"));
    }

    @Test
    void should_listOnlySuministrosOfThatRol_whenMultipleRolesHaveSuministros() {
        String idRolA = RolResourceTest.crearRolCompleto("AREA-SUM-2A", "CARGO-SUM-2A", "ROL-SUM-2A");
        String idRolB = RolResourceTest.crearRolCompleto("AREA-SUM-2B", "CARGO-SUM-2B", "ROL-SUM-2B");

        given().contentType("application/json").body("{\"nombre\":\"SAP\",\"prioridad\":\"ALTA\"}")
                .when().post("/api/roles/" + idRolA + "/suministros").then().statusCode(201);
        given().contentType("application/json").body("{\"nombre\":\"VPN\",\"prioridad\":\"MEDIA\"}")
                .when().post("/api/roles/" + idRolA + "/suministros").then().statusCode(201);
        given().contentType("application/json").body("{\"nombre\":\"OTRO\",\"prioridad\":\"BAJA\"}")
                .when().post("/api/roles/" + idRolB + "/suministros").then().statusCode(201);

        given()
                .when().get("/api/roles/" + idRolA + "/suministros")
                .then()
                .statusCode(200)
                .body("$", hasSize(2));
    }

    @Test
    void should_return404_whenCreatingSuministroUnderNonexistentRol() {
        given()
                .contentType("application/json")
                .body("{\"nombre\":\"CORREO\",\"prioridad\":\"ALTA\"}")
                .when().post("/api/roles/rol-inexistente-999/suministros")
                .then()
                .statusCode(404);
    }

    @Test
    void should_return404_whenSuministroIdDoesNotExistUnderRol() {
        String idRol = RolResourceTest.crearRolCompleto("AREA-SUM-3", "CARGO-SUM-3", "ROL-SUM-3");

        given()
                .when().get("/api/roles/" + idRol + "/suministros/no-existe-999")
                .then()
                .statusCode(404);
    }
}
