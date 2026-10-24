package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;

/**
 * Tests de integración contra el LocalStack real de Quarkus Dev Services
 * para los endpoints de Atributo/Rol de la feature 4
 * ({@code crud_rol_suministro_atributo}): replica de
 * {@code CargoRepository.findCargo}, {@code AtributoRepository.findAppArea2},
 * y las 4 categorías de suministro.
 *
 * <p>Cada test arma su propio grafo Aplicacion/Area/Cargo(x2)/Rol(x2)/
 * Suministro/Atributo para no interferir con otros tests.
 */
@QuarkusTest
class AtributoResourceTest {

    @Test
    void should_listOnlyActiveAtributos_whenQueryingByAplicacionAndArea() {
        Escenario e = crearEscenario("APP-2-3");

        String rol1Roles = crearAtributo(e.aplicacion, e.rol1, e.suministro1, "ROLES", "COD1", "Atributo Roles", "ACTIVO");
        String rol1Grupos = crearAtributo(e.aplicacion, e.rol1, e.suministro1, "GRUPOS", "COD2", "Atributo Grupos", "ACTIVO");
        crearAtributo(e.aplicacion, e.rol2, e.suministro2, "INBOX", "COD3", "Atributo Inbox", "ACTIVO");
        // Inactivo -- no debe aparecer (equivalente a "e.id = 1" del legado).
        crearAtributo(e.aplicacion, e.rol1, e.suministro1, "DEFAULTINBOX", "COD4", "Atributo Inactivo", "INACTIVO");

        given()
                .queryParam("aplicacion", e.aplicacion)
                .queryParam("area", e.area)
                .when().get("/api/atributos")
                .then()
                .statusCode(200)
                .body("$", hasSize(3))
                .body("atributoCodigo", hasItem("COD1"))
                .body("atributoCodigo", hasItem("COD2"))
                .body("atributoCodigo", hasItem("COD3"));
    }

    @Test
    void should_listOnlyAtributosOfThatCargo_whenQueryingByAplicacionAreaAndCargo() {
        Escenario e = crearEscenario("APP-PATRON4");

        crearAtributo(e.aplicacion, e.rol1, e.suministro1, "ROLES", "C1", "Atributo Cargo1", "ACTIVO");
        crearAtributo(e.aplicacion, e.rol2, e.suministro2, "ROLES", "C2", "Atributo Cargo2", "ACTIVO");

        given()
                .queryParam("aplicacion", e.aplicacion)
                .queryParam("area", e.area)
                .queryParam("cargo", e.cargo1)
                .when().get("/api/atributos")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].atributoCodigo", is("C1"))
                .body("[0].rolId", is(e.rol1));
    }

    @Test
    void should_groupRolesUnderTheirCargo_whenQueryingCargosConRoles() {
        Escenario e = crearEscenario("APP-CARGOSROLES");

        crearAtributo(e.aplicacion, e.rol1, e.suministro1, "ROLES", "G1", "Atributo1", "ACTIVO");
        crearAtributo(e.aplicacion, e.rol2, e.suministro2, "ROLES", "G2", "Atributo2", "ACTIVO");

        given()
                .queryParam("aplicacion", e.aplicacion)
                .queryParam("area", e.area)
                .when().get("/api/roles/cargos")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("cargoCodigo", hasItem(e.cargo1))
                .body("cargoCodigo", hasItem(e.cargo2))
                .body("find { it.cargoCodigo == '" + e.cargo1 + "' }.roles[0].id", is(e.rol1))
                .body("find { it.cargoCodigo == '" + e.cargo2 + "' }.roles[0].id", is(e.rol2));
    }

    @Test
    void should_filterByCategoria_whenQueryingRolCategoriaEndpoint() {
        Escenario e = crearEscenario("APP-CATEGORIAS");

        crearAtributo(e.aplicacion, e.rol1, e.suministro1, "ROLES", "R1", "Atributo Roles", "ACTIVO");
        crearAtributo(e.aplicacion, e.rol1, e.suministro1, "GRUPOS", "R2", "Atributo Grupos", "ACTIVO");
        crearAtributo(e.aplicacion, e.rol1, e.suministro1, "INBOX", "R3", "Atributo Inbox", "ACTIVO");

        given()
                .queryParam("aplicacion", e.aplicacion)
                .when().get("/api/atributos/rol/" + e.rol1 + "/categoria/GRUPOS")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].atributoCodigo", is("R2"))
                .body("[0].categoria", is("GRUPOS"));

        // DEFAULTINBOX no tiene ningun atributo creado para este rol -- lista vacia, no error.
        given()
                .queryParam("aplicacion", e.aplicacion)
                .when().get("/api/atributos/rol/" + e.rol1 + "/categoria/DEFAULTINBOX")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void should_return400_whenCategoriaIsInvalid() {
        Escenario e = crearEscenario("APP-CATBAD");

        given()
                .queryParam("aplicacion", e.aplicacion)
                .when().get("/api/atributos/rol/" + e.rol1 + "/categoria/NOEXISTE")
                .then()
                .statusCode(400);
    }

    @Test
    void should_return404_whenCreatingAtributoWithNonexistentSuministro() {
        Escenario e = crearEscenario("APP-ATRIBUTOBAD");

        given()
                .contentType("application/json")
                .body(atributoJson(e.aplicacion, e.rol1, "suministro-inexistente-999", "ROLES", "X", "X", "ACTIVO"))
                .when().post("/api/atributos")
                .then()
                .statusCode(404);
    }

    private record Escenario(String aplicacion, String area, String cargo1, String cargo2, String rol1,
            String rol2, String suministro1, String suministro2) {
    }

    private Escenario crearEscenario(String prefijo) {
        String aplicacion = RolResourceTest.crearAplicacion("APLICACION-" + prefijo);
        String ubicacion = RolResourceTest.crearUbicacion("UBIC-" + prefijo);
        String area = RolResourceTest.crearArea("AREA-" + prefijo);
        String cargo1 = RolResourceTest.crearCargo("CARGO1-" + prefijo);
        String cargo2 = RolResourceTest.crearCargo("CARGO2-" + prefijo);

        String rol1 = given().contentType("application/json")
                .body(RolResourceTest.rolJson(ubicacion, area, cargo1, "ROL1-" + prefijo))
                .when().post("/api/roles").then().statusCode(201).extract().path("id").toString();
        String rol2 = given().contentType("application/json")
                .body(RolResourceTest.rolJson(ubicacion, area, cargo2, "ROL2-" + prefijo))
                .when().post("/api/roles").then().statusCode(201).extract().path("id").toString();

        String suministro1 = given().contentType("application/json")
                .body("{\"nombre\":\"SUM1\",\"prioridad\":\"ALTA\"}")
                .when().post("/api/roles/" + rol1 + "/suministros").then().statusCode(201)
                .extract().path("id").toString();
        String suministro2 = given().contentType("application/json")
                .body("{\"nombre\":\"SUM2\",\"prioridad\":\"ALTA\"}")
                .when().post("/api/roles/" + rol2 + "/suministros").then().statusCode(201)
                .extract().path("id").toString();

        return new Escenario(aplicacion, area, cargo1, cargo2, rol1, rol2, suministro1, suministro2);
    }

    private static String crearAtributo(String aplicacion, String rolId, String suministroId, String categoria,
            String atributoCodigo, String atributoNombre, String estado) {
        return given()
                .contentType("application/json")
                .body(atributoJson(aplicacion, rolId, suministroId, categoria, atributoCodigo, atributoNombre, estado))
                .when().post("/api/atributos")
                .then().statusCode(201)
                .extract().path("id").toString();
    }

    private static String atributoJson(String aplicacion, String rolId, String suministroId, String categoria,
            String atributoCodigo, String atributoNombre, String estado) {
        return "{"
                + "\"aplicacionCodigo\":\"" + aplicacion + "\","
                + "\"rolId\":\"" + rolId + "\","
                + "\"suministroId\":\"" + suministroId + "\","
                + "\"categoria\":\"" + categoria + "\","
                + "\"atributoCodigo\":\"" + atributoCodigo + "\","
                + "\"atributoNombre\":\"" + atributoNombre + "\","
                + "\"estado\":\"" + estado + "\"}";
    }
}
