package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * Contrato reactivo común de los 5 repositorios de catálogo simple (Área,
 * Cargo, Ubicación, Aplicación, Estado). Implementado por
 * {@link CatalogoSimpleRepository} y expuesto como bean CDI por cada
 * repositorio concreto (ej. {@code AreaRepository}) delegando a una
 * instancia interna parametrizada.
 */
public interface CatalogoRepositorio<T> {

    Uni<T> buscarPorCodigo(String codigo);

    Multi<T> listarTodos();

    Uni<T> crear(String nombre);

    Uni<T> actualizar(String codigo, String nombre);

    Uni<Void> eliminar(String codigo);
}
