package pe.edu.cibertec.proyemp.domain;

/**
 * Catálogo simple Cargo (paridad con {@code CargoManagedBean} del legacy).
 * Ver {@link Area} para la decisión de qué campo legado mapea a
 * {@code codigo}.
 */
public record Cargo(String codigo, String nombre) {
}
