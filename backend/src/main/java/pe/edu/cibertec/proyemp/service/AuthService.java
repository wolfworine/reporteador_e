package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.CredencialesInvalidasException;
import pe.edu.cibertec.proyemp.domain.Usuario;
import pe.edu.cibertec.proyemp.domain.UsuarioNoEncontradoException;
import pe.edu.cibertec.proyemp.repository.UsuarioRepository;

/**
 * Reemplazo reactivo de {@code LoginManagedBean.iniciarSesion()}. El legacy
 * hasheaba con {@code StringMD.getStringMessageDigest(password, SHA256)}
 * (SHA-256 sin sal, comparación por igualdad de string en la query JPQL
 * {@code UsuarioRepository.findbyEmailandPassword}); esta clase nunca
 * compara contraseñas en texto plano ni las descifra -- compara el password
 * en texto plano recibido contra el hash BCrypt guardado, usando
 * {@link PasswordHasher#matches(String, String)}.
 */
@ApplicationScoped
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordHasher passwordHasher;

    @Inject
    public AuthService(UsuarioRepository usuarioRepository, PasswordHasher passwordHasher) {
        this.usuarioRepository = usuarioRepository;
        this.passwordHasher = passwordHasher;
    }

    /**
     * Falla con {@link CredencialesInvalidasException} (mapeada a HTTP 401)
     * tanto si el email no existe como si el password no coincide -- nunca
     * distingue los dos casos en la respuesta (ver el Javadoc de esa
     * excepción).
     */
    public Uni<Usuario> login(String email, String passwordPlano) {
        if (email == null || email.isBlank() || passwordPlano == null || passwordPlano.isBlank()) {
            return Uni.createFrom().failure(new CredencialesInvalidasException());
        }
        return usuarioRepository.buscarPorEmail(email)
                .onFailure(UsuarioNoEncontradoException.class)
                .transform(failure -> new CredencialesInvalidasException())
                .onItem().transform(usuario -> {
                    if (!passwordHasher.matches(passwordPlano, usuario.passwordHash())) {
                        throw new CredencialesInvalidasException();
                    }
                    return usuario;
                });
    }
}
