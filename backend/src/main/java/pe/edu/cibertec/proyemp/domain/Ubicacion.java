package pe.edu.cibertec.proyemp.domain;

/**
 * Catálogo simple Ubicación (paridad con {@code UbicacionManagedBean} del
 * legacy). El legacy nombra el campo descriptivo {@code ubicacion} (no
 * {@code nombre}); se renombra a {@code nombre} aquí para uniformidad entre
 * los 5 catálogos de esta feature (decisión documentada en
 * {@code progress/current.md}). Ver {@link Area} para la decisión de qué
 * campo legado mapea a {@code codigo}.
 */
public record Ubicacion(String codigo, String nombre) {
}
