package pe.edu.cibertec.proyemp.web.dto;

import pe.edu.cibertec.proyemp.domain.TipoUsuario;
import pe.edu.cibertec.proyemp.domain.Usuario;

/**
 * Respuesta JSON de Usuario. Deliberadamente NO incluye
 * {@code passwordHash} -- a diferencia de Rol/catálogos (que devuelven su
 * record de dominio directo porque no tienen campo secreto), ningún
 * endpoint de Usuario/Auth serializa jamás el dominio {@link Usuario} crudo,
 * precisamente para que sea estructuralmente imposible filtrar el hash en
 * una respuesta (ver docs/verification.md, checklist de seguridad).
 */
public record UsuarioResponse(
        String id,
        String nombres,
        String apellidos,
        String email,
        TipoUsuario tipo) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.id(), usuario.nombres(), usuario.apellidos(), usuario.email(),
                usuario.tipo());
    }
}
