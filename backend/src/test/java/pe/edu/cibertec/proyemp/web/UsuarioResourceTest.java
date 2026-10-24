package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.proyemp.repository.RoleRepositoryTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integración de {@code /api/usuarios} contra el LocalStack real de
 * Quarkus Dev Services (feature 5, {@code usuario_login_password_hash_fix}).
 *
 * <p>Cubre los acceptance criteria 2 y 3 de {@code feature_list.json}
 * directamente sobre el dato guardado en DynamoDB (no solo sobre la
 * respuesta HTTP): el hash tiene prefijo {@code $2} (BCrypt), y dos altas
 * con la misma contraseña producen hashes distintos (sal).
 */
@QuarkusTest
class UsuarioResourceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    DynamoDbAsyncClient dynamoDbAsyncClient;

    @Test
    void should_returnCreatedUsuarioWithoutPasswordFields_whenPostingNewUsuario() {
        given()
                .contentType("application/json")
                .body(usuarioJson("Juan", "Perez", uniqueEmail("juan"), "clave123", "OPERADOR"))
                .when().post("/api/usuarios")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("nombres", is("Juan"))
                .body("apellidos", is("Perez"))
                .body("tipo", is("OPERADOR"))
                .body("passwordHash", nullValue())
                .body("password", nullValue());
    }

    @Test
    void should_findSameUsuario_whenSearchingByIdAfterCreate() {
        String id = crearUsuario("Maria", "Lopez", uniqueEmail("maria"), "otraClave456", "ADMINISTRADOR");

        given()
                .when().get("/api/usuarios/" + id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("nombres", is("Maria"))
                .body("tipo", is("ADMINISTRADOR"))
                .body("passwordHash", nullValue());
    }

    @Test
    void should_return404_whenUsuarioIdDoesNotExist() {
        given()
                .when().get("/api/usuarios/no-existe-999")
                .then()
                .statusCode(404);
    }

    @Test
    void should_return409_whenCreatingUsuarioWithDuplicateEmail() {
        String email = uniqueEmail("duplicado");
        crearUsuario("Primero", "Uno", email, "clave1", "OPERADOR");

        given()
                .contentType("application/json")
                .body(usuarioJson("Segundo", "Dos", email, "clave2", "OPERADOR"))
                .when().post("/api/usuarios")
                .then()
                .statusCode(409);
    }

    @Test
    void should_deleteUsuario_whenUsuarioExists() {
        String id = crearUsuario("Borrar", "Me", uniqueEmail("borrar"), "claveBorrar", "OPERADOR");

        given().when().delete("/api/usuarios/" + id).then().statusCode(204);
        given().when().get("/api/usuarios/" + id).then().statusCode(404);
    }

    @Test
    void should_storeBcryptHashAndDifferentHashesForSamePassword_whenTwoUsuariosShareThePassword() {
        String passwordCompartido = "mismaClave789";
        String id1 = crearUsuario("Uno", "Compartido", uniqueEmail("compartido1"), passwordCompartido, "OPERADOR");
        String id2 = crearUsuario("Dos", "Compartido", uniqueEmail("compartido2"), passwordCompartido, "OPERADOR");

        String hash1 = leerPasswordHashCrudo(id1);
        String hash2 = leerPasswordHashCrudo(id2);

        assertTrue(hash1.startsWith("$2"), "El hash guardado debe tener el prefijo BCrypt ($2...): " + hash1);
        assertTrue(hash2.startsWith("$2"), "El hash guardado debe tener el prefijo BCrypt ($2...): " + hash2);
        assertNotEquals(hash1, hash2, "Dos usuarios con la misma contraseña deben tener hashes distintos (sal)");
    }

    private String leerPasswordHashCrudo(String idUsuario) {
        Map<String, AttributeValue> key = Map.of(
                RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.usuarioPk(idUsuario)),
                RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));

        Uni<GetItemResponse> read = Uni.createFrom()
                .completionStage(() -> dynamoDbAsyncClient.getItem(GetItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(key)
                        .build()));

        GetItemResponse response = read.await().atMost(TIMEOUT);
        return response.item().get(RoleRepositoryTable.ATTR_PASSWORD_HASH).s();
    }

    static String crearUsuario(String nombres, String apellidos, String email, String password, String tipo) {
        return given()
                .contentType("application/json")
                .body(usuarioJson(nombres, apellidos, email, password, tipo))
                .when().post("/api/usuarios")
                .then().statusCode(201)
                .extract().path("id").toString();
    }

    static String usuarioJson(String nombres, String apellidos, String email, String password, String tipo) {
        return "{"
                + "\"nombres\":\"" + nombres + "\","
                + "\"apellidos\":\"" + apellidos + "\","
                + "\"email\":\"" + email + "\","
                + "\"password\":\"" + password + "\","
                + "\"tipo\":\"" + tipo + "\"}";
    }

    static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.com";
    }
}
