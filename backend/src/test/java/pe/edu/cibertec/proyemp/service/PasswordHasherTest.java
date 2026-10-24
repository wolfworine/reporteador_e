package pe.edu.cibertec.proyemp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Test unitario puro (sin {@code @QuarkusTest}, sin LocalStack) de
 * {@link PasswordHasher} -- feature 5, {@code usuario_login_password_hash_fix}.
 * Cubre el acceptance criterio 3 de {@code feature_list.json}: "dos usuarios
 * con la misma contraseña tienen hashes distintos (salt), y el login falla
 * con contraseña incorrecta" -- a nivel del hasher puro; la parte de
 * integración end-to-end (dos altas de Usuario reales + login) está en
 * {@code web/UsuarioResourceTest}/{@code web/AuthResourceTest}.
 */
class PasswordHasherTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void should_produceHashStartingWithBcryptPrefix_whenHashingAnyPassword() {
        String hash = passwordHasher.hash("miPassword123");

        assertTrue(hash.startsWith("$2"), "El hash debe tener el prefijo BCrypt ($2...), no: " + hash);
    }

    @Test
    void should_produceDifferentHashes_whenHashingSamePasswordTwice() {
        String hash1 = passwordHasher.hash("mismoPassword");
        String hash2 = passwordHasher.hash("mismoPassword");

        assertNotEquals(hash1, hash2, "BCrypt debe generar una sal aleatoria distinta en cada llamada");
    }

    @Test
    void should_verify_whenPasswordMatchesStoredHash() {
        String hash = passwordHasher.hash("correcto123");

        assertTrue(passwordHasher.matches("correcto123", hash));
    }

    @Test
    void should_notVerify_whenPasswordDoesNotMatchStoredHash() {
        String hash = passwordHasher.hash("correcto123");

        assertFalse(passwordHasher.matches("incorrecto999", hash));
    }
}
