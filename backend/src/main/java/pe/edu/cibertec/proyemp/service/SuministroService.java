package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Suministro;
import pe.edu.cibertec.proyemp.infra.SnapshotCache;
import pe.edu.cibertec.proyemp.repository.RolRepository;
import pe.edu.cibertec.proyemp.repository.SuministroRepository;

/**
 * Servicio reactivo de {@link Suministro}, paridad funcional con
 * {@code SuministroManagedBean} del legacy. Al crear, valida que el Rol
 * padre exista (el esquema DynamoDB no impone FKs); al listar, no valida --
 * un Rol sin Suministros simplemente devuelve una lista vacía, igual que la
 * Query subyacente (Patrón 2b).
 */
@ApplicationScoped
public class SuministroService {

    private final SuministroRepository suministroRepository;
    private final RolRepository rolRepository;
    private final SnapshotCache snapshot;

    @Inject
    public SuministroService(SuministroRepository suministroRepository, RolRepository rolRepository,
            SnapshotCache snapshot) {
        this.suministroRepository = suministroRepository;
        this.rolRepository = rolRepository;
        this.snapshot = snapshot;
    }

    public Uni<Suministro> crear(String idRol, String nombre, String prioridad) {
        if (nombre == null || nombre.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("El nombre del suministro es obligatorio"));
        }
        return rolRepository.buscarPorId(idRol)
                .chain(ignored -> suministroRepository.crear(idRol, nombre, prioridad))
                .invoke(() -> snapshot.invalidar("atributos:"));
    }

    public Uni<Suministro> buscarPorId(String idRol, String idSuministro) {
        return suministroRepository.buscarPorId(idRol, idSuministro);
    }

    public Uni<Suministro> actualizar(String idRol, String idSuministro, String nombre, String prioridad) {
        if (nombre == null || nombre.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("El nombre del suministro es obligatorio"));
        }
        return suministroRepository.actualizar(idRol, idSuministro, nombre, prioridad)
                .invoke(() -> snapshot.invalidar("atributos:"));
    }

    public Uni<Void> eliminar(String idRol, String idSuministro) {
        return suministroRepository.eliminar(idRol, idSuministro).invoke(() -> snapshot.invalidar("atributos:"));
    }

    public Multi<Suministro> listarPorRol(String idRol) {
        return suministroRepository.listarPorRol(idRol);
    }
}
