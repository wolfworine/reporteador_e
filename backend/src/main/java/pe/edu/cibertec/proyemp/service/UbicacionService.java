package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Ubicacion;
import pe.edu.cibertec.proyemp.infra.SnapshotCache;
import pe.edu.cibertec.proyemp.repository.UbicacionRepository;

/**
 * Servicio reactivo de {@link Ubicacion}, paridad funcional con
 * {@code UbicacionManagedBean} del legacy.
 */
@ApplicationScoped
public class UbicacionService {

    private final CatalogoSimpleService<Ubicacion> delegado;

    @Inject
    public UbicacionService(UbicacionRepository repository, SnapshotCache snapshot) {
        this.delegado = new CatalogoSimpleService<>(repository, snapshot, "ubicacion");
    }

    public Multi<Ubicacion> listarTodos() {
        return delegado.listarTodos();
    }

    public Uni<Ubicacion> buscarPorCodigo(String codigo) {
        return delegado.buscarPorCodigo(codigo);
    }

    public Uni<Ubicacion> crear(String nombre) {
        return delegado.crear(nombre);
    }

    public Uni<Ubicacion> actualizar(String codigo, String nombre) {
        return delegado.actualizar(codigo, nombre);
    }

    public Uni<Void> eliminar(String codigo) {
        return delegado.eliminar(codigo);
    }
}
