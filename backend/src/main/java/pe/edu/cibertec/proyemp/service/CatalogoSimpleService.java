package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import pe.edu.cibertec.proyemp.infra.SnapshotCache;
import pe.edu.cibertec.proyemp.repository.CatalogoRepositorio;

import java.util.List;

/**
 * Lógica de negocio reactiva común a los 5 catálogos simples (Área, Cargo,
 * Ubicación, Aplicación, Estado): valida el nombre antes de delegar en el
 * repositorio. No es un bean CDI -- cada servicio concreto (ej.
 * {@code AreaService}) construye una instancia interna parametrizada con su
 * propio {@link CatalogoRepositorio}, igual patrón de composición que
 * {@code repository/CatalogoSimpleRepository}.
 */
public final class CatalogoSimpleService<T> {

    private final CatalogoRepositorio<T> repositorio;
    private final SnapshotCache snapshot;
    private final String namespace;

    public CatalogoSimpleService(CatalogoRepositorio<T> repositorio, SnapshotCache snapshot, String namespace) {
        this.repositorio = repositorio;
        this.snapshot = snapshot;
        this.namespace = namespace + ":";
    }

    /** Lecturas servidas desde el snapshot en memoria (ver {@link SnapshotCache}). */
    public Multi<T> listarTodos() {
        return snapshot.obtener(namespace + "todos", () -> repositorio.listarTodos().collect().asList()
                        .onItem().transform(List::copyOf))
                .onItem().transformToMulti(lista -> Multi.createFrom().iterable(lista));
    }

    public Uni<T> buscarPorCodigo(String codigo) {
        return snapshot.obtener(namespace + "codigo:" + codigo, () -> repositorio.buscarPorCodigo(codigo));
    }

    public Uni<T> crear(String nombre) {
        return validarNombre(nombre).chain(() -> repositorio.crear(nombre)).invoke(this::invalidar);
    }

    public Uni<T> actualizar(String codigo, String nombre) {
        return validarNombre(nombre).chain(() -> repositorio.actualizar(codigo, nombre)).invoke(this::invalidar);
    }

    public Uni<Void> eliminar(String codigo) {
        return repositorio.eliminar(codigo).invoke(this::invalidar);
    }

    private void invalidar() {
        snapshot.invalidar(namespace);
        // Los atributos llevan nombres de cargo/rol denormalizados.
        snapshot.invalidar("atributos:");
    }

    private Uni<Void> validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("El nombre es obligatorio"));
        }
        return Uni.createFrom().voidItem();
    }
}
