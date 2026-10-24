package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Test de integración de {@code /api/roles} contra el LocalStack real de
 * Quarkus Dev Services (feature 4, {@code crud_rol_suministro_atributo}).
 * Cada test crea sus propios catálogos (Ubicación/Área/Cargo) para no
 * depender de datos previos.
 */
@QuarkusTest
class RolResourceTest {

    @Test
    void should_returnCreatedRolWithGeneratedId_whenPostingNewRolWithValidCatalogReferences() {
        String ubicacion = crearUbicacion("LIMA");
        String area = crearArea("TI");
        String cargo = crearCargo("DESARROLLADOR");

        given()
                .contentType("application/json")
                .body(rolJson(ubicacion, area, cargo, "ROL DESARROLLO"))
                .when().post("/api/roles")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("nombre", is("ROL DESARROLLO"))
                .body("areaCodigo", is(area))
                .body("cargoCodigo", is(cargo));
    }

    @Test
    void should_findSameRol_whenSearchingByIdAfterCreate() {
        String ubicacion = crearUbicacion("AREQUIPA");
        String area = crearArea("FINANZAS");
        String cargo = crearCargo("ANALISTA FINANCIERO");

        String id = given()
                .contentType("application/json")
                .body(rolJson(ubicacion, area, cargo, "ROL ANALISTA"))
                .when().post("/api/roles")
                .then()
                .statusCode(201)
                .extract().path("id").toString();

        given()
                .when().get("/api/roles/" + id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("nombre", is("ROL ANALISTA"));
    }

    @Test
    void should_return404_whenRolIdDoesNotExist() {
        given()
                .when().get("/api/roles/no-existe-999")
                .then()
                .statusCode(404);
    }

    @Test
    void should_return404_whenCreatingRolWithNonexistentAreaCodigo() {
        String ubicacion = crearUbicacion("CUSCO");
        String cargo = crearCargo("SUPERVISOR");

        given()
                .contentType("application/json")
                .body(rolJson(ubicacion, "area-inexistente-999", cargo, "ROL INVALIDO"))
                .when().post("/api/roles")
                .then()
                .statusCode(404);
    }

    @Test
    void should_deleteRol_whenRolExists() {
        String ubicacion = crearUbicacion("TRUJILLO");
        String area = crearArea("LOGISTICA");
        String cargo = crearCargo("COORDINADOR");

        String id = given()
                .contentType("application/json")
                .body(rolJson(ubicacion, area, cargo, "ROL A ELIMINAR"))
                .when().post("/api/roles")
                .then()
                .statusCode(201)
                .extract().path("id").toString();

        given().when().delete("/api/roles/" + id).then().statusCode(204);
        given().when().get("/api/roles/" + id).then().statusCode(404);
    }

    static String crearUbicacion(String nombre) {
        return given()
                .contentType("application/json")
                .body("{\"nombre\":\"" + nombre + "\"}")
                .when().post("/api/ubicaciones")
                .then().statusCode(201)
                .extract().path("codigo").toString();
    }

    static String crearArea(String nombre) {
        return given()
                .contentType("application/json")
                .body("{\"nombre\":\"" + nombre + "\"}")
                .when().post("/api/areas")
                .then().statusCode(201)
                .extract().path("codigo").toString();
    }

    static String crearCargo(String nombre) {
        return given()
                .contentType("application/json")
                .body("{\"nombre\":\"" + nombre + "\"}")
                .when().post("/api/cargos")
                .then().statusCode(201)
                .extract().path("codigo").toString();
    }

    static String crearAplicacion(String nombre) {
        return given()
                .contentType("application/json")
                .body("{\"nombre\":\"" + nombre + "\"}")
                .when().post("/api/aplicaciones")
                .then().statusCode(201)
                .extract().path("codigo").toString();
    }

    /** Crea un Rol completo (con sus catálogos) y devuelve su id -- reusado por otros tests de la feature 4. */
    static String crearRolCompleto(String areaNombre, String cargoNombre, String rolNombre) {
        String ubicacion = crearUbicacion("UBIC-" + rolNombre);
        String area = crearArea(areaNombre);
        String cargo = crearCargo(cargoNombre);
        return given()
                .contentType("application/json")
                .body(rolJson(ubicacion, area, cargo, rolNombre))
                .when().post("/api/roles")
                .then().statusCode(201)
                .extract().path("id").toString();
    }

    static String rolJson(String ubicacion, String area, String cargo, String nombre) {
        return "{"
                + "\"ubicacionCodigo\":\"" + ubicacion + "\","
                + "\"areaCodigo\":\"" + area + "\","
                + "\"cargoCodigo\":\"" + cargo + "\","
                + "\"nombre\":\"" + nombre + "\","
                + "\"descripcion\":\"descripcion de prueba\","
                + "\"tipo\":\"NORMAL\","
                + "\"reglaNegocio\":\"ninguna\","
                + "\"ambito\":\"local\","
                + "\"propietarioRol\":\"TI\","
                + "\"propietarioPersona\":\"admin\","
                + "\"situacion\":\"ACTIVO\","
                + "\"rolBase\":\"NO\"}";
    }
}
