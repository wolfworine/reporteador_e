package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Atributo;
import pe.edu.cibertec.proyemp.domain.CargoConRoles;
import pe.edu.cibertec.proyemp.domain.CategoriaSuministro;
import pe.edu.cibertec.proyemp.domain.EstadoAtributo;
import pe.edu.cibertec.proyemp.domain.RolResumen;
import pe.edu.cibertec.proyemp.infra.SnapshotCache;
import pe.edu.cibertec.proyemp.repository.AplicacionRepository;
import pe.edu.cibertec.proyemp.repository.AtributoRepository;
import pe.edu.cibertec.proyemp.repository.CargoRepository;
import pe.edu.cibertec.proyemp.repository.RolRepository;
import pe.edu.cibertec.proyemp.repository.SuministroRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Servicio reactivo de {@link Atributo}. No implementa CRUD completo (fuera
 * del acceptance de esta feature) -- solo {@code crear} (para poblar datos
 * de prueba, resolviendo y validando Aplicación/Rol/Cargo/Suministro antes
 * de denormalizar y persistir) y las consultas de los patrones 2/3/4 +
 * "4 categorías" del esquema canónico.
 */
@ApplicationScoped
public class AtributoService {

    private final AtributoRepository atributoRepository;
    private final AplicacionRepository aplicacionRepository;
    private final RolRepository rolRepository;
    private final CargoRepository cargoRepository;
    private final SuministroRepository suministroRepository;
    private final SnapshotCache snapshot;

    @Inject
    public AtributoService(AtributoRepository atributoRepository, AplicacionRepository aplicacionRepository,
            RolRepository rolRepository, CargoRepository cargoRepository, SuministroRepository suministroRepository,
            SnapshotCache snapshot) {
        this.atributoRepository = atributoRepository;
        this.aplicacionRepository = aplicacionRepository;
        this.rolRepository = rolRepository;
        this.cargoRepository = cargoRepository;
        this.suministroRepository = suministroRepository;
        this.snapshot = snapshot;
    }

    public Uni<Atributo> crear(String aplicacionCodigo, String rolId, String suministroId, String categoriaStr,
            String atributoCodigo, String atributoNombre, String estadoStr) {
        return Uni.createFrom().item(() -> parseCategoriaYEstado(categoriaStr, estadoStr))
                .chain(categoriaEstado -> aplicacionRepository.buscarPorCodigo(aplicacionCodigo)
                        .chain(ignored -> rolRepository.buscarPorId(rolId))
                        .chain(rol -> cargoRepository.buscarPorCodigo(rol.cargoCodigo())
                                .chain(cargo -> suministroRepository.buscarPorId(rolId, suministroId)
                                        .chain(ignored -> atributoRepository.crear(new Atributo(
                                                null, null, categoriaEstado.categoria(), aplicacionCodigo,
                                                rol.areaCodigo(), rol.cargoCodigo(), cargo.nombre(), rolId,
                                                rol.tipo(), rol.nombre(), suministroId, atributoCodigo,
                                                atributoNombre, categoriaEstado.estado()))))))
                .invoke(() -> snapshot.invalidar("atributos:"));
    }

    private record CategoriaEstado(CategoriaSuministro categoria, EstadoAtributo estado) {
    }

    private CategoriaEstado parseCategoriaYEstado(String categoriaStr, String estadoStr) {
        return new CategoriaEstado(parseCategoria(categoriaStr), parseEstado(estadoStr));
    }

    /**
     * Patrones 2/3 ({@code findAppArea}). Los listados de atributos de esta
     * clase se sirven desde el snapshot en memoria ({@link SnapshotCache}).
     */
    public Multi<Atributo> listarPorAplicacionArea(String idAplicacion, String idArea) {
        return desdeSnapshot("atributos:app-area:" + idAplicacion + ":" + idArea,
                () -> atributoRepository.listarPorAplicacionArea(idAplicacion, idArea));
    }

    /** Patrón 4 ({@code findAppArea2}). */
    public Multi<Atributo> listarPorAplicacionAreaCargo(String idAplicacion, String idArea, String idCargo) {
        return desdeSnapshot("atributos:app-area-cargo:" + idAplicacion + ":" + idArea + ":" + idCargo,
                () -> atributoRepository.listarPorAplicacionAreaCargo(idAplicacion, idArea, idCargo));
    }

    private Multi<Atributo> desdeSnapshot(String clave, Supplier<Multi<Atributo>> consulta) {
        return snapshot.obtener(clave, () -> consulta.get().collect().asList().onItem().transform(List::copyOf))
                .onItem().transformToMulti(lista -> Multi.createFrom().iterable(lista));
    }

    /** Una de las 4 categorías de suministro, para un Rol dado. */
    public Multi<Atributo> listarPorCategoria(String idRol, String idAplicacion, String categoriaStr) {
        CategoriaSuministro categoria = parseCategoria(categoriaStr);
        return rolRepository.buscarPorId(idRol)
                .onItem().transformToMulti(rol -> atributoRepository
                        .listarPorRolYCategoria(idAplicacion, rol.areaCodigo(), idRol, categoria));
    }

    /**
     * Forma agrupada del endpoint que replica {@code findCargo(aplicacion, area)}
     * -- ver {@link CargoConRoles} para la decisión de forma de respuesta.
     */
    public Uni<List<CargoConRoles>> listarCargosConRoles(String idAplicacion, String idArea) {
        return snapshot.obtener("atributos:cargos-roles:" + idAplicacion + ":" + idArea,
                () -> atributoRepository.listarPorAplicacionArea(idAplicacion, idArea)
                        .collect().asList()
                        .onItem().transform(AtributoService::agruparPorCargo));
    }

    private static List<CargoConRoles> agruparPorCargo(List<Atributo> atributos) {
        Map<String, String> nombrePorCargo = new LinkedHashMap<>();
        Map<String, Map<String, RolResumen>> rolesPorCargo = new LinkedHashMap<>();

        for (Atributo atributo : atributos) {
            nombrePorCargo.putIfAbsent(atributo.cargoCodigo(), atributo.cargoNombre());
            rolesPorCargo.computeIfAbsent(atributo.cargoCodigo(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(atributo.rolId(), new RolResumen(atributo.rolId(), atributo.rolNombre(), atributo.rolTipo()));
        }

        List<CargoConRoles> resultado = new ArrayList<>();
        for (Map.Entry<String, String> entry : nombrePorCargo.entrySet()) {
            String cargoCodigo = entry.getKey();
            resultado.add(new CargoConRoles(cargoCodigo, entry.getValue(),
                    List.copyOf(rolesPorCargo.get(cargoCodigo).values())));
        }
        return resultado;
    }

    private CategoriaSuministro parseCategoria(String categoriaStr) {
        try {
            return CategoriaSuministro.valueOf(categoriaStr == null ? null : categoriaStr.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException(
                    "Categoria invalida '" + categoriaStr + "', valores validos: ROLES, GRUPOS, INBOX, DEFAULTINBOX");
        }
    }

    private EstadoAtributo parseEstado(String estadoStr) {
        try {
            return EstadoAtributo.valueOf(estadoStr == null ? null : estadoStr.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("Estado invalido '" + estadoStr + "', valores validos: ACTIVO, INACTIVO");
        }
    }
}
