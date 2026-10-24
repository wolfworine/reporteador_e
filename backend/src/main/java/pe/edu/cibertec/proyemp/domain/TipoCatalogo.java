package pe.edu.cibertec.proyemp.domain;

/**
 * Discriminador {@code <tipo>} del patrón 1 (catálogos simples) del esquema
 * canónico de {@code ../database/docs/architecture.md}:
 * {@code PK = "ENTITY#<tipo>#<codigo>"}, {@code SK = "METADATA"}.
 *
 * <p>{@link #name()} se usa tal cual como el literal {@code <tipo>} de la
 * clave (ej. {@code TipoCatalogo.AREA.name()} -&gt; {@code "AREA"}), igual
 * que en {@code RoleRepositoryKeys.catalogPk} del track {@code database/}.
 */
public enum TipoCatalogo {
    AREA,
    CARGO,
    UBICACION,
    APLICACION,
    ESTADO
}
