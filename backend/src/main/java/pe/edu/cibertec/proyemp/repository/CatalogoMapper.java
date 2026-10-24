package pe.edu.cibertec.proyemp.repository;

/**
 * Fábrica de un objeto de dominio de catálogo simple a partir de sus dos
 * campos persistidos ({@code codigo}, {@code nombre}). Permite que
 * {@link CatalogoSimpleRepository} sea genérico sin reflexión: cada
 * repositorio concreto (ej. {@code AreaRepository}) pasa una referencia a
 * método como {@code Area::new}.
 */
@FunctionalInterface
public interface CatalogoMapper<T> {
    T construir(String codigo, String nombre);
}
