package pe.edu.cibertec.proyemp.web.dto;

import pe.edu.cibertec.proyemp.domain.TipoUsuario;

/**
 * Cuerpo de request de {@code POST /api/usuarios}. Deliberadamente NO tiene
 * un método {@code aDominio()} como los demás DTOs de request (ej.
 * {@code RolRequest}) -- el campo {@code password} es texto plano y nunca
 * debe convertirse directamente en el {@code passwordHash} de
 * {@link pe.edu.cibertec.proyemp.domain.Usuario}; el hashing lo hace
 * explícitamente {@code service/UsuarioService.crear(...)} vía
 * {@code service/PasswordHasher}, para que no exista ningún camino de
 * código donde un valor con el mismo nombre de campo termine persistido sin
 * pasar por el hasher.
 */
public record UsuarioRequest(
        String nombres,
        String apellidos,
        String email,
        String password,
        TipoUsuario tipo) {
}
