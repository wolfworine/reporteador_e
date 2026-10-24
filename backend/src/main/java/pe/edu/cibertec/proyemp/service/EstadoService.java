package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Estado;
import pe.edu.cibertec.proyemp.infra.SnapshotCache;
import pe.edu.cibertec.proyemp.repository.EstadoRepository;

/**
 * Servicio reactivo de {@link Estado}. El legacy no tiene
 * {@code EstadoManagedBean} propio (solo {@code repository}/{@code service}
 * genéricos vía Spring Data), pero esta feature expone CRUD explícito para
 * los 5 catálogos según su acceptance criteria.
 */
@ApplicationScoped
public class EstadoService {

    private final CatalogoSimpleService<Estado> delegado;

    @Inject
    public EstadoService(EstadoRepository repository, SnapshotCache snapshot) {
        this.delegado = new CatalogoSimpleService<>(repository, snapshot, "estado");
    }

    public Multi<Estado> listarTodos() {
        return delegado.listarTodos();
    }

    public Uni<Estado> buscarPorCodigo(String codigo) {
        return delegado.buscarPorCodigo(codigo);
    }

    public Uni<Estado> crear(String nombre) {
        return delegado.crear(nombre);
    }

    public Uni<Estado> actualizar(String codigo, String nombre) {
        return delegado.actualizar(codigo, nombre);
    }

    public Uni<Void> eliminar(String codigo) {
        return delegado.eliminar(codigo);
    }
}
