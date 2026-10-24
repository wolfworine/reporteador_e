package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Cargo;
import pe.edu.cibertec.proyemp.infra.SnapshotCache;
import pe.edu.cibertec.proyemp.repository.CargoRepository;

/**
 * Servicio reactivo de {@link Cargo}, paridad funcional con
 * {@code CargoManagedBean} del legacy.
 */
@ApplicationScoped
public class CargoService {

    private final CatalogoSimpleService<Cargo> delegado;

    @Inject
    public CargoService(CargoRepository repository, SnapshotCache snapshot) {
        this.delegado = new CatalogoSimpleService<>(repository, snapshot, "cargo");
    }

    public Multi<Cargo> listarTodos() {
        return delegado.listarTodos();
    }

    public Uni<Cargo> buscarPorCodigo(String codigo) {
        return delegado.buscarPorCodigo(codigo);
    }

    public Uni<Cargo> crear(String nombre) {
        return delegado.crear(nombre);
    }

    public Uni<Cargo> actualizar(String codigo, String nombre) {
        return delegado.actualizar(codigo, nombre);
    }

    public Uni<Void> eliminar(String codigo) {
        return delegado.eliminar(codigo);
    }
}
