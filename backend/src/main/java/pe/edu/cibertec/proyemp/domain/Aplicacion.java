package pe.edu.cibertec.proyemp.domain;

/**
 * Catálogo simple Aplicación (paridad con {@code AplicacionManagedBean} del
 * legacy). Ver {@link Area} para la decisión de qué campo legado mapea a
 * {@code codigo}.
 */
public record Aplicacion(String codigo, String nombre) {
}
