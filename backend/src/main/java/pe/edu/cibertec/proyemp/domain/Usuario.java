package pe.edu.cibertec.proyemp.domain;

/**
 * Usuario (paridad de campos con {@code model.Usuario} del legacy, prefijos
 * {@code a_}/{@code b_}/... quitados por trazabilidad de nombre, no de
 * significado: {@code a_firstname}->{@code nombres},
 * {@code b_lastname}->{@code apellidos}, {@code c_email}->{@code email},
 * {@code d_password}->{@code passwordHash}, {@code e_tipo}->{@code tipo}).
 *
 * <p><b>Decisión de diseño (feature 5): {@code model.Login} del legacy NO se
 * migra como entidad separada.</b> Es una tabla espejo de
 * email/password sin relación FK con {@code Usuario} (columnas propias, sin
 * {@code @ManyToOne}); su único uso real confirmado por grep es en el bloque
 * comentado (código muerto, nunca compilado en producción) de
 * {@code LoginManagedBean.java} líneas ~234-314, que además usa
 * {@code Encripcion.Encriptar(...)} (la otra clase de cifrado reversible
 * prohibida). El único camino de login que de verdad se ejecuta
 * ({@code LoginManagedBean.iniciarSesion()}) consulta exclusivamente
 * {@code UsuarioRepository.findbyEmailandPassword(email, encriptado)} --
 * nunca toca {@code LoginRepository}. Fusionar ambas en una sola entidad
 * {@code Usuario} elimina una fuente de inconsistencia (dos copias de la
 * misma contraseña que el legacy nunca sincronizaba de forma confiable,
 * ver {@code cambiarpass()} comentado) sin perder ningún comportamiento
 * vivo. Documentado también en {@code progress/current.md}.
 *
 * <p>{@code passwordHash} nunca es el password en texto plano ni un valor
 * reversible -- siempre el resultado de {@code service/PasswordHasher} (BCrypt,
 * prefijo {@code $2}). Nunca se serializa en una respuesta HTTP (ver
 * {@code web/dto/UsuarioResponse}, que deliberadamente lo omite).
 *
 * <p>Persistido en la tabla única {@code RoleRepository} bajo
 * {@code PK=USUARIO#<id>}, {@code SK=METADATA}, con una fila puntero
 * adicional {@code PK=EMAIL#<email>} para lookup directo en el login -- ver
 * {@code repository/RoleRepositoryTable.java} y {@code progress/current.md}.
 */
public record Usuario(
        String id,
        String nombres,
        String apellidos,
        String email,
        String passwordHash,
        TipoUsuario tipo) {
}
