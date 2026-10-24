package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.TipoUsuario;
import pe.edu.cibertec.proyemp.domain.Usuario;
import pe.edu.cibertec.proyemp.repository.UsuarioRepository;

/**
 * Servicio reactivo de {@link Usuario}, paridad funcional con
 * {@code UsuarioManagedBean} del legacy (alta/lectura/baja) menos
 * actualización (asimetría intencional documentada en
 * {@code progress/current.md}, mismo criterio que la asimetría de
 * {@code Atributo} en la feature 4 -- ningún acceptance de esta feature
 * exige actualizar Usuario, y actualizar el email requeriría mover
 * atómicamente la fila puntero, complejidad no justificada en este pase).
 *
 * <p>Hashea el password en texto plano recibido de {@code web/} ANTES de
 * llegar al repositorio -- el repositorio nunca ve ni persiste texto plano.
 */
@ApplicationScoped
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordHasher passwordHasher;

    @Inject
    public UsuarioService(UsuarioRepository usuarioRepository, PasswordHasher passwordHasher) {
        this.usuarioRepository = usuarioRepository;
        this.passwordHasher = passwordHasher;
    }

    public Uni<Usuario> crear(String nombres, String apellidos, String email, String passwordPlano,
            TipoUsuario tipo) {
        if (passwordPlano == null || passwordPlano.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("El password es obligatorio"));
        }
        if (email == null || email.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("El email es obligatorio"));
        }
        String hash = passwordHasher.hash(passwordPlano);
        Usuario datos = new Usuario(null, nombres, apellidos, email, hash, tipo);
        return usuarioRepository.crear(datos);
    }

    public Uni<Usuario> buscarPorId(String id) {
        return usuarioRepository.buscarPorId(id);
    }

    public Uni<Void> eliminar(String id) {
        return usuarioRepository.eliminar(id);
    }

    public Multi<Usuario> listarTodos() {
        return usuarioRepository.listarTodos();
    }
}
