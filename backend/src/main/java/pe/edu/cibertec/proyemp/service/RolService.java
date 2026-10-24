package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Rol;
import pe.edu.cibertec.proyemp.infra.SnapshotCache;
import pe.edu.cibertec.proyemp.repository.AreaRepository;
import pe.edu.cibertec.proyemp.repository.CargoRepository;
import pe.edu.cibertec.proyemp.repository.RolRepository;
import pe.edu.cibertec.proyemp.repository.UbicacionRepository;

/**
 * Servicio reactivo de {@link Rol}, paridad funcional con
 * {@code RolManagedBean} del legacy. Antes de crear/actualizar un Rol,
 * valida que Ubicación/Área/Cargo referenciados existan en sus catálogos
 * (feature 3) -- decisión de cuánta validación cruzada vale la pena para
 * esta feature, documentada en {@code progress/current.md}: sin esta
 * validación, un Rol podría apuntar a un código de catálogo inexistente sin
 * ningún error hasta que se intentara leerlo en un endpoint agregado (ej.
 * {@code GET /api/roles/cargos}).
 */
@ApplicationScoped
public class RolService {

    private final RolRepository rolRepository;
    private final UbicacionRepository ubicacionRepository;
    private final AreaRepository areaRepository;
    private final CargoRepository cargoRepository;
    private final SnapshotCache snapshot;

    @Inject
    public RolService(RolRepository rolRepository, UbicacionRepository ubicacionRepository,
            AreaRepository areaRepository, CargoRepository cargoRepository, SnapshotCache snapshot) {
        this.rolRepository = rolRepository;
        this.ubicacionRepository = ubicacionRepository;
        this.areaRepository = areaRepository;
        this.cargoRepository = cargoRepository;
        this.snapshot = snapshot;
    }

    public Uni<Rol> crear(Rol datos) {
        return validar(datos).chain(() -> rolRepository.crear(datos)).invoke(this::invalidarAtributos);
    }

    public Uni<Rol> buscarPorId(String id) {
        return rolRepository.buscarPorId(id);
    }

    public Uni<Rol> actualizar(String id, Rol datos) {
        return validar(datos).chain(() -> rolRepository.actualizar(id, datos)).invoke(this::invalidarAtributos);
    }

    public Uni<Void> eliminar(String id) {
        return rolRepository.eliminar(id).invoke(this::invalidarAtributos);
    }

    /** Los atributos llevan el nombre/tipo del rol denormalizado: sus snapshots quedan vencidos. */
    private void invalidarAtributos() {
        snapshot.invalidar("atributos:");
    }

    public Multi<Rol> listarTodos() {
        return rolRepository.listarTodos();
    }

    private Uni<Void> validar(Rol datos) {
        if (datos.nombre() == null || datos.nombre().isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("El nombre del rol es obligatorio"));
        }
        return ubicacionRepository.buscarPorCodigo(datos.ubicacionCodigo())
                .chain(ignored -> areaRepository.buscarPorCodigo(datos.areaCodigo()))
                .chain(ignored -> cargoRepository.buscarPorCodigo(datos.cargoCodigo()))
                .replaceWithVoid();
    }
}
