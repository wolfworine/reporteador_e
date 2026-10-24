package pe.edu.cibertec.proyemp.domain;

/**
 * Rol (paridad de campos con {@code model.Rol} del legacy, prefijos
 * {@code a_}/{@code b_}/... quitados por trazabilidad de nombre, no de
 * significado). {@code id} es el ID legado preservado tal cual (ver
 * {@code ../database/docs/architecture.md} §"Generación de IDs") cuando el
 * dato viene migrado, o el siguiente valor del contador atómico
 * {@code COUNTER#ROL} cuando lo crea esta aplicación.
 *
 * <p>{@code ubicacionCodigo}/{@code areaCodigo}/{@code cargoCodigo} son los
 * mismos códigos que identifican los catálogos simples de la feature 3
 * ({@link Ubicacion#codigo()}, {@link Area#codigo()}, {@link Cargo#codigo()}).
 *
 * <p>Persistido en la tabla única {@code RoleRepository} bajo
 * {@code PK=ROL#<id>}, {@code SK=METADATA} -- ver
 * {@code repository/RoleRepositoryTable.java} y {@code progress/current.md}
 * para la decisión de por qué esta fila (no enumerada explícitamente en el
 * documento canónico de {@code database/}, que solo cubre patrones de
 * lectura) es una extensión coherente del mismo esquema.
 */
public record Rol(
        String id,
        String ubicacionCodigo,
        String areaCodigo,
        String cargoCodigo,
        String nombre,
        String descripcion,
        String tipo,
        String reglaNegocio,
        String ambito,
        String propietarioRol,
        String propietarioPersona,
        String situacion,
        String rolBase) {
}
