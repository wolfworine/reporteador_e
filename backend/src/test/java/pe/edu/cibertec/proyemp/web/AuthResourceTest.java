package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static pe.edu.cibertec.proyemp.web.UsuarioResourceTest.crearUsuario;
import static pe.edu.cibertec.proyemp.web.UsuarioResourceTest.uniqueEmail;

/**
 * Test de integración de {@code POST /api/auth/login} contra el LocalStack
 * real de Quarkus Dev Services (feature 5,
 * {@code usuario_login_password_hash_fix}). Cubre la mitad restante del
 * acceptance criterio 3 de {@code feature_list.json}: "el login falla con
 * contraseña incorrecta" -- reemplazo reactivo de
 * {@code LoginManagedBean.iniciarSesion()}.
 */
@QuarkusTest
class AuthResourceTest {

    @Test
    void should_returnUsuarioDataWithoutPasswordHash_whenLoginWithCorrectPassword() {
        String email = uniqueEmail("login-ok");
        crearUsuario("Login", "Correcto", email, "passwordCorrecto", "ADMINISTRADOR");

        given()
                .contentType("application/json")
                .body(loginJson(email, "passwordCorrecto"))
                .when().post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("email", is(email))
                .body("tipo", is("ADMINISTRADOR"))
                .body("passwordHash", nullValue());
    }

    @Test
    void should_return401_whenLoginWithWrongPassword() {
        String email = uniqueEmail("login-mal");
        crearUsuario("Login", "Malo", email, "passwordCorrecto", "OPERADOR");

        given()
                .contentType("application/json")
                .body(loginJson(email, "passwordIncorrecto"))
                .when().post("/api/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void should_return401_whenLoginWithNonexistentEmail() {
        given()
                .contentType("application/json")
                .body(loginJson(uniqueEmail("no-existe"), "cualquierPassword"))
                .when().post("/api/auth/login")
                .then()
                .statusCode(401);
    }

    static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
