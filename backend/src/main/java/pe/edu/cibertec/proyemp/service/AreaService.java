package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Area;
import pe.edu.cibertec.proyemp.infra.SnapshotCache;
import pe.edu.cibertec.proyemp.repository.AreaRepository;

/**
 * Servicio reactivo de {@link Area}, paridad funcional con
 * {@code AreaManagedBean} del legacy (listar, crear/registrar,
 * actualizar/modificar, eliminar).
 */
@ApplicationScoped
public class AreaService {

    private final CatalogoSimpleService<Area> delegado;

    @Inject
    public AreaService(AreaRepository repository, SnapshotCache snapshot) {
        this.delegado = new CatalogoSimpleService<>(repository, snapshot, "area");
    }

    public Multi<Area> listarTodos() {
        return delegado.listarTodos();
    }

    public Uni<Area> buscarPorCodigo(String codigo) {
        return delegado.buscarPorCodigo(codigo);
    }

    public Uni<Area> crear(String nombre) {
        return delegado.crear(nombre);
    }

    public Uni<Area> actualizar(String codigo, String nombre) {
        return delegado.actualizar(codigo, nombre);
    }

    public Uni<Void> eliminar(String codigo) {
        return delegado.eliminar(codigo);
    }
}
