package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Aplicacion;
import pe.edu.cibertec.proyemp.infra.SnapshotCache;
import pe.edu.cibertec.proyemp.repository.AplicacionRepository;

/**
 * Servicio reactivo de {@link Aplicacion}, paridad funcional con
 * {@code AplicacionManagedBean} del legacy.
 */
@ApplicationScoped
public class AplicacionService {

    private final CatalogoSimpleService<Aplicacion> delegado;

    @Inject
    public AplicacionService(AplicacionRepository repository, SnapshotCache snapshot) {
        this.delegado = new CatalogoSimpleService<>(repository, snapshot, "aplicacion");
    }

    public Multi<Aplicacion> listarTodos() {
        return delegado.listarTodos();
    }

    public Uni<Aplicacion> buscarPorCodigo(String codigo) {
        return delegado.buscarPorCodigo(codigo);
    }

    public Uni<Aplicacion> crear(String nombre) {
        return delegado.crear(nombre);
    }

    public Uni<Aplicacion> actualizar(String codigo, String nombre) {
        return delegado.actualizar(codigo, nombre);
    }

    public Uni<Void> eliminar(String codigo) {
        return delegado.eliminar(codigo);
    }
}
