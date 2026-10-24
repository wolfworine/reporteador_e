package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Aplicacion;
import pe.edu.cibertec.proyemp.domain.Area;
import pe.edu.cibertec.proyemp.domain.Atributo;
import pe.edu.cibertec.proyemp.domain.CargoConRoles;
import pe.edu.cibertec.proyemp.domain.CategoriaSuministro;
import pe.edu.cibertec.proyemp.domain.DetalleRol;
import pe.edu.cibertec.proyemp.domain.Reporte;
import pe.edu.cibertec.proyemp.domain.RolResumen;
import pe.edu.cibertec.proyemp.repository.AplicacionRepository;
import pe.edu.cibertec.proyemp.repository.AreaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensambla el reporte consolidado Cargo→Rol→4 categorías de suministro
 * (Roles/Grupos/Inbox/DefaultInbox), paridad funcional con
 * {@code ReporteManagedBean.reporte()} del legacy, componiendo únicamente
 * servicios/repositorios ya existentes de las features 3
 * ({@link AreaRepository}, {@link AplicacionRepository}) y 4
 * ({@link AtributoService}) -- esta clase no accede a DynamoDB
 * directamente, no repite ninguna {@code QueryRequest}.
 *
 * <p><b>Sin Kafka, sin PDF/Excel, sin endpoint HTTP propio</b> -- esta
 * feature (7) solo deja lista y probada la lógica de ensamblado de datos
 * que las features 8 ({@code reportes_adaptadores_pdf_excel}) y 9
 * ({@code reportes_generacion_async_kafka}) van a reutilizar.
 *
 * <h2>El bug de duplicados del legacy y por qué esta query no lo reproduce</h2>
 *
 * <p>En {@code ReporteManagedBean.reporte()}, {@code listaCargos =
 * cargoService.getCargoRepository().findCargoxArea(area.getCodigo())} se
 * dedupea con {@code HashSet} inmediatamente después de la consulta -- señal
 * de que el JPQL de origen (join Cargo→Rol/Atributo) devuelve una fila de
 * Cargo por cada Rol/Atributo asociado, no una fila por Cargo. En
 * {@code imprimir()} se repite el mismo síntoma tres veces más: se vuelve a
 * dedupear {@code reportes} con otro {@code HashSet}, y también, por cada
 * {@code DetalleRol}, las 4 listas de {@code Detalle}
 * ({@code suministroRoles}/{@code Grupos}/{@code Inbox}/{@code Defaultinbox})
 * -- evidencia de que {@code DetalleService.getDetalleRepository()
 * .ListarSuministroAtributoxParametro(...)} también trae filas repetidas.
 *
 * <p>Esta clase no reintroduce ese patrón en ningún nivel:
 * <ul>
 *   <li><b>Cargo→Rol:</b> {@link AtributoService#listarCargosConRoles} (ya
 *   `done` en la feature 4) agrupa con {@code LinkedHashMap}/
 *   {@code putIfAbsent} por {@code cargoCodigo} y por {@code rolId} -- cada
 *   Cargo y cada Rol aparece una sola vez por construcción del agrupado, no
 *   por post-filtrado con {@code Set} después de una query que trae
 *   duplicados.</li>
 *   <li><b>Rol→4 categorías:</b> {@link AtributoService#listarPorCategoria}
 *   consulta {@code AtributoRepository.listarPorRolYCategoria} vía
 *   {@code GSI1} filtrando por {@code rolId}+{@code categoria}+
 *   {@code estado=ACTIVO} -- cada {@link Atributo} es un ítem DynamoDB
 *   distinto (no hay join relacional que multiplique filas), así que la
 *   lista resultante nunca trae duplicados que limpiar.</li>
 * </ul>
 */
@ApplicationScoped
public class ReporteService {

    private final AtributoService atributoService;
    private final AreaRepository areaRepository;
    private final AplicacionRepository aplicacionRepository;

    @Inject
    public ReporteService(AtributoService atributoService, AreaRepository areaRepository,
            AplicacionRepository aplicacionRepository) {
        this.atributoService = atributoService;
        this.areaRepository = areaRepository;
        this.aplicacionRepository = aplicacionRepository;
    }

    /**
     * Reporte consolidado (uno por Cargo) para una Aplicación+Área dadas.
     * Un Área sin ningún Cargo/Rol con atributos activos devuelve una lista
     * vacía, no un error -- mismo criterio que
     * {@link AtributoService#listarCargosConRoles}.
     */
    public Uni<List<Reporte>> generar(String idAplicacion, String idArea) {
        return Uni.combine().all()
                .unis(areaRepository.buscarPorCodigo(idArea), aplicacionRepository.buscarPorCodigo(idAplicacion),
                        atributoService.listarCargosConRoles(idAplicacion, idArea))
                .asTuple()
                .chain(tuple -> armarReportes(tuple.getItem1(), tuple.getItem2(), tuple.getItem3(), idAplicacion));
    }

    private Uni<List<Reporte>> armarReportes(Area area, Aplicacion aplicacion, List<CargoConRoles> cargosConRoles,
            String idAplicacion) {
        List<Uni<Reporte>> reportesUnis = cargosConRoles.stream()
                .map(cargo -> armarReporte(area, aplicacion, cargo, idAplicacion))
                .toList();
        if (reportesUnis.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        return Uni.join().all(reportesUnis).andFailFast();
    }

    private Uni<Reporte> armarReporte(Area area, Aplicacion aplicacion, CargoConRoles cargo, String idAplicacion) {
        List<Uni<DetalleRol>> detallesUnis = cargo.roles().stream()
                .map(rol -> armarDetalleRol(rol, aplicacion, idAplicacion))
                .toList();
        Uni<List<DetalleRol>> detallesUni = detallesUnis.isEmpty()
                ? Uni.createFrom().item(List.<DetalleRol>of())
                : Uni.join().all(detallesUnis).andFailFast();

        return detallesUni.onItem().transform(detalles -> {
            int cantidadSuministro = detalles.stream().mapToInt(DetalleRol::cantidadSuministroRol).sum();
            return new Reporte(area.codigo(), area.nombre(), cargo.cargoCodigo(), cargo.cargoNombre(), detalles,
                    detalles.size(), cantidadSuministro);
        });
    }

    private Uni<DetalleRol> armarDetalleRol(RolResumen rol, Aplicacion aplicacion, String idAplicacion) {
        Uni<List<Atributo>> rolesUni = atributoService
                .listarPorCategoria(rol.id(), idAplicacion, CategoriaSuministro.ROLES.name()).collect().asList();
        Uni<List<Atributo>> gruposUni = atributoService
                .listarPorCategoria(rol.id(), idAplicacion, CategoriaSuministro.GRUPOS.name()).collect().asList();
        Uni<List<Atributo>> inboxUni = atributoService
                .listarPorCategoria(rol.id(), idAplicacion, CategoriaSuministro.INBOX.name()).collect().asList();
        Uni<List<Atributo>> defaultInboxUni = atributoService
                .listarPorCategoria(rol.id(), idAplicacion, CategoriaSuministro.DEFAULTINBOX.name()).collect()
                .asList();

        return Uni.combine().all().unis(rolesUni, gruposUni, inboxUni, defaultInboxUni)
                .asTuple()
                .onItem().transform(tuple -> construirDetalleRol(rol, aplicacion, tuple.getItem1(), tuple.getItem2(),
                        tuple.getItem3(), tuple.getItem4()));
    }

    private DetalleRol construirDetalleRol(RolResumen rol, Aplicacion aplicacion, List<Atributo> roles,
            List<Atributo> grupos, List<Atributo> inbox, List<Atributo> defaultInbox) {
        List<CategoriaSuministro> tiposConDatos = new ArrayList<>();
        if (!roles.isEmpty()) {
            tiposConDatos.add(CategoriaSuministro.ROLES);
        }
        if (!grupos.isEmpty()) {
            tiposConDatos.add(CategoriaSuministro.GRUPOS);
        }
        if (!inbox.isEmpty()) {
            tiposConDatos.add(CategoriaSuministro.INBOX);
        }
        if (!defaultInbox.isEmpty()) {
            tiposConDatos.add(CategoriaSuministro.DEFAULTINBOX);
        }

        int cantidadSuministroRol = roles.size() + grupos.size() + inbox.size() + defaultInbox.size();

        return new DetalleRol(rol.id(), rol.nombre(), tipoRol(rol.tipo()), aplicacion.nombre(), tiposConDatos, roles,
                grupos, inbox, defaultInbox, cantidadSuministroRol);
    }

    /** Paridad con {@code ReporteManagedBean.TipoRol(String)} del legacy. */
    private String tipoRol(String tipoCrudo) {
        return "DINAMICO".equalsIgnoreCase(tipoCrudo) ? "BASE" : "ADICIONAL";
    }
}
