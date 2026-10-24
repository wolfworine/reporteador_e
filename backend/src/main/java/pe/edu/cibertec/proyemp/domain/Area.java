package pe.edu.cibertec.proyemp.domain;

/**
 * Catálogo simple Área (paridad con {@code AreaManagedBean} del legacy).
 *
 * <p>{@code codigo} corresponde al ID numérico autogenerado legado (columna
 * {@code ID} de la tabla {@code area}, la misma que referencian las FKs
 * reales como {@code Rol.b_area}) representado como {@code String} para
 * encajar como literal de PK de DynamoDB -- ver
 * {@code progress/current.md} para la decisión completa de por qué se elige
 * este campo y no el código de negocio corto ({@code Area.id} /
 * {@code ID_AREA} en el legacy).
 */
public record Area(String codigo, String nombre) {
}
