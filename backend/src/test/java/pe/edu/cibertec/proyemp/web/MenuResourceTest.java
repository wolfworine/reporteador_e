package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.hasSize;

/**
 * Tests de integración de {@code GET /api/menu/{tipoUsuario}} contra el
 * LocalStack real de Quarkus Dev Services (feature 6,
 * {@code menu_dinamico_por_tipo_usuario}).
 *
 * <p>Nota de diseño: a diferencia de otros recursos (que scopean sus
 * queries por un código de negocio único, ej. {@code aplicacion}/{@code area}
 * en {@code AtributoResourceTest}), {@code GET /api/menu/{tipoUsuario}} solo
 * scopea por {@code tipoUsuario} -- los datos de Menu se acumulan en la
 * misma tabla a través de toda la corrida de tests. Por eso las
 * aserciones aquí buscan el nodo creado por su propio {@code codigo}
 * (generado por el contador atómico, único por alta) dentro del árbol
 * devuelto, en vez de afirmar el tamaño total del array raíz.
 */
@QuarkusTest
class MenuResourceTest {

    @Test
    void should_buildParentChildHierarchyUsingCodSubmenu_whenItemsBelongToASubmenu() {
        String submenu = crearMenu("Administracion", "SUBMENU", "OPERADOR", null, "ACTIVO", null);
        String item1 = crearMenu("Usuarios", "ITEM", "OPERADOR", submenu, "ACTIVO", "/usuarios");
        String item2 = crearMenu("Roles", "ITEM", "OPERADOR", submenu, "ACTIVO", "/roles");

        given()
                .when().get("/api/menu/OPERADOR")
                .then()
                .statusCode(200)
                .body("find { it.codigo == '" + submenu + "' }.nombre", is("Administracion"))
                .body("find { it.codigo == '" + submenu + "' }.hijos", hasSize(2))
                .body("find { it.codigo == '" + submenu + "' }.hijos.codigo", hasItem(item1))
                .body("find { it.codigo == '" + submenu + "' }.hijos.codigo", hasItem(item2))
                .body("find { it.codigo == '" + submenu + "' }.hijos.find { it.codigo == '" + item1 + "' }.action",
                        is("/usuarios"));
    }

    /**
     * Test de regresión del bug de origen: {@code MenuController.
     * establecerPermisos()} del legacy agregaba TODOS los ítems del
     * tipoUsuario a CADA submenú (producto cruzado), ignorando
     * {@code cod_submenu} por completo (confirmado por grep: ese campo solo
     * aparece en getter/setter/toString de {@code model.Menu.java}). Este
     * test falla contra esa lógica vieja (cada submenú tendría los ítems
     * del OTRO submenú también) y pasa contra la nueva, que enlaza
     * estrictamente por {@code codSubmenu == submenu.codigo}.
     */
    @Test
    void should_notCrossJoinItemsAcrossDifferentSubmenus_whenTwoSubmenusExistForSameTipoUsuario() {
        String submenuA = crearMenu("Reportes", "SUBMENU", "ADMINISTRADOR", null, "ACTIVO", null);
        String submenuB = crearMenu("Seguridad", "SUBMENU", "ADMINISTRADOR", null, "ACTIVO", null);
        String itemA = crearMenu("Reporte PDF", "ITEM", "ADMINISTRADOR", submenuA, "ACTIVO", "/reportes/pdf");
        String itemB = crearMenu("Cambiar clave", "ITEM", "ADMINISTRADOR", submenuB, "ACTIVO", "/seguridad/clave");

        given()
                .when().get("/api/menu/ADMINISTRADOR")
                .then()
                .statusCode(200)
                .body("find { it.codigo == '" + submenuA + "' }.hijos", hasSize(1))
                .body("find { it.codigo == '" + submenuA + "' }.hijos.codigo", hasItem(itemA))
                .body("find { it.codigo == '" + submenuA + "' }.hijos.codigo", not(hasItem(itemB)))
                .body("find { it.codigo == '" + submenuB + "' }.hijos", hasSize(1))
                .body("find { it.codigo == '" + submenuB + "' }.hijos.codigo", hasItem(itemB))
                .body("find { it.codigo == '" + submenuB + "' }.hijos.codigo", not(hasItem(itemA)));
    }

    @Test
    void should_excludeInactiveAndDifferentTipoUsuarioItems_whenBuildingTree() {
        String submenu = crearMenu("Panel", "SUBMENU", "OPERADOR", null, "ACTIVO", null);
        String itemActivo = crearMenu("Bandeja", "ITEM", "OPERADOR", submenu, "ACTIVO", "/bandeja");
        String itemInactivo = crearMenu("Fuera de servicio", "ITEM", "OPERADOR", submenu, "INACTIVO", "/viejo");
        String itemOtroTipo = crearMenu("Solo admin", "ITEM", "ADMINISTRADOR", submenu, "ACTIVO", "/admin-only");
        String submenuInactivo = crearMenu("Deshabilitado", "SUBMENU", "OPERADOR", null, "INACTIVO", null);

        given()
                .when().get("/api/menu/OPERADOR")
                .then()
                .statusCode(200)
                .body("find { it.codigo == '" + submenu + "' }.hijos", hasSize(1))
                .body("find { it.codigo == '" + submenu + "' }.hijos.codigo", hasItem(itemActivo))
                .body("find { it.codigo == '" + submenu + "' }.hijos.codigo", not(hasItem(itemInactivo)))
                .body("find { it.codigo == '" + submenu + "' }.hijos.codigo", not(hasItem(itemOtroTipo)))
                .body("codigo", not(hasItem(submenuInactivo)));
    }

    @Test
    void should_returnDifferentMenus_whenQueryingTwoDifferentTipoUsuario() {
        String submenuAdmin = crearMenu("Solo Administrador", "SUBMENU", "ADMINISTRADOR", null, "ACTIVO", null);
        String itemAdmin = crearMenu("Auditoria", "ITEM", "ADMINISTRADOR", submenuAdmin, "ACTIVO", "/auditoria");
        String submenuOperador = crearMenu("Solo Operador", "SUBMENU", "OPERADOR", null, "ACTIVO", null);
        String itemOperador = crearMenu("Consulta", "ITEM", "OPERADOR", submenuOperador, "ACTIVO", "/consulta");

        given()
                .when().get("/api/menu/ADMINISTRADOR")
                .then()
                .statusCode(200)
                .body("codigo", hasItem(submenuAdmin))
                .body("codigo", not(hasItem(submenuOperador)))
                .body("find { it.codigo == '" + submenuAdmin + "' }.hijos.codigo", hasItem(itemAdmin));

        given()
                .when().get("/api/menu/OPERADOR")
                .then()
                .statusCode(200)
                .body("codigo", hasItem(submenuOperador))
                .body("codigo", not(hasItem(submenuAdmin)))
                .body("find { it.codigo == '" + submenuOperador + "' }.hijos.codigo", hasItem(itemOperador));
    }

    @Test
    void should_return400_whenTipoUsuarioIsInvalid() {
        given()
                .when().get("/api/menu/NO-EXISTE")
                .then()
                .statusCode(400);
    }

    private static String crearMenu(String nombre, String tipo, String tipoUsuario, String codSubmenu, String estado,
            String action) {
        return given()
                .contentType("application/json")
                .body(menuJson(nombre, tipo, tipoUsuario, codSubmenu, estado, action))
                .when().post("/api/menu")
                .then().statusCode(201)
                .extract().path("codigo").toString();
    }

    private static String menuJson(String nombre, String tipo, String tipoUsuario, String codSubmenu, String estado,
            String action) {
        return "{"
                + "\"nombre\":\"" + nombre + "\","
                + "\"tipo\":\"" + tipo + "\","
                + "\"tipoUsuario\":\"" + tipoUsuario + "\","
                + "\"codSubmenu\":" + (codSubmenu == null ? "null" : "\"" + codSubmenu + "\"") + ","
                + "\"estado\":\"" + estado + "\","
                + "\"action\":" + (action == null ? "null" : "\"" + action + "\"")
                + "}";
    }
}
