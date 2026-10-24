package pe.edu.cibertec.proyemp.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Envoltorio de {@code at.favre.lib:bcrypt} -- el reemplazo aprobado
 * (docs/conventions.md) de {@code util/Cripto.java} (AES/ECB reversible,
 * prohibido) y de {@code util/StringMD.java} (SHA-256 sin sal, el mecanismo
 * realmente usado por {@code LoginManagedBean.iniciarSesion()} en el
 * legacy). BCrypt genera una sal aleatoria distinta en cada llamada a
 * {@link #hash(String)} -- dos contraseñas idénticas producen hashes
 * distintos, ver {@code service/PasswordHasherTest.java}
 * ({@code should_produceDifferentHashes_whenHashingSamePasswordTwice}).
 *
 * <p>Cost factor 12 (default recomendado por la librería en 2026, balance
 * entre resistencia a fuerza bruta y latencia de login) -- no configurable
 * desde fuera de esta clase a propósito, para que no se pueda bajar por
 * error desde otra capa.
 */
@ApplicationScoped
public class PasswordHasher {

    private static final int COST_FACTOR = 12;

    /** Hashea un password en texto plano. Nunca reversible -- no existe un método "unhash". */
    public String hash(String passwordPlano) {
        return BCrypt.withDefaults().hashToString(COST_FACTOR, passwordPlano.toCharArray());
    }

    /** Compara un password en texto plano contra un hash BCrypt ya guardado. Nunca compara texto plano contra texto plano. */
    public boolean matches(String passwordPlano, String hashGuardado) {
        return BCrypt.verifyer().verify(passwordPlano.toCharArray(), hashGuardado).verified;
    }
}
