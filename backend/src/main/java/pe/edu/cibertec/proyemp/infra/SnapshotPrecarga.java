package pe.edu.cibertec.proyemp.infra;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import pe.edu.cibertec.proyemp.domain.TipoUsuario;
import pe.edu.cibertec.proyemp.service.AplicacionService;
import pe.edu.cibertec.proyemp.service.AreaService;
import pe.edu.cibertec.proyemp.service.CargoService;
import pe.edu.cibertec.proyemp.service.EstadoService;
import pe.edu.cibertec.proyemp.service.MenuService;
import pe.edu.cibertec.proyemp.service.UbicacionService;

import java.time.Duration;
import java.util.List;

/**
 * Calienta el {@link SnapshotCache} al arrancar: carga catálogos y menús
 * antes de recibir tráfico, para que la primera consulta del usuario ya
 * encuentre los datos en memoria (y el cliente DynamoDB, ya conectado) en
 * vez de pagar ese costo en su primera petición.
 *
 * <p>Corre DESPUÉS de los bootstraps de tabla/secretos (prioridad mayor =
 * se ejecuta más tarde). Si DynamoDB no responde, solo se registra un aviso:
 * el arranque no falla, el snapshot se llenará con las primeras lecturas.
 * {@code .await()} acotado permitido aquí por la misma excepción explícita
 * de arranque que ya usan los initializers (docs/conventions.md).
 */
@ApplicationScoped
public class SnapshotPrecarga {

    private static final Logger LOG = Logger.getLogger(SnapshotPrecarga.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final AplicacionService aplicaciones;
    private final AreaService areas;
    private final CargoService cargos;
    private final EstadoService estados;
    private final UbicacionService ubicaciones;
    private final MenuService menu;

    @Inject
    public SnapshotPrecarga(AplicacionService aplicaciones, AreaService areas, CargoService cargos,
            EstadoService estados, UbicacionService ubicaciones, MenuService menu) {
        this.aplicaciones = aplicaciones;
        this.areas = areas;
        this.cargos = cargos;
        this.estados = estados;
        this.ubicaciones = ubicaciones;
        this.menu = menu;
    }

    void onStart(@Observes @Priority(3000) StartupEvent event) {
        long inicio = System.currentTimeMillis();
        try {
            Uni.join().all(List.of(
                            aplicaciones.listarTodos().collect().asList().replaceWithVoid(),
                            areas.listarTodos().collect().asList().replaceWithVoid(),
                            cargos.listarTodos().collect().asList().replaceWithVoid(),
                            estados.listarTodos().collect().asList().replaceWithVoid(),
                            ubicaciones.listarTodos().collect().asList().replaceWithVoid(),
                            menu.obtenerArbol(TipoUsuario.ADMINISTRADOR).replaceWithVoid(),
                            menu.obtenerArbol(TipoUsuario.OPERADOR).replaceWithVoid()))
                    .andCollectFailures()
                    .await().atMost(TIMEOUT);
            LOG.infof("Snapshot en memoria precargado en %d ms", System.currentTimeMillis() - inicio);
        } catch (Exception ex) {
            LOG.warn("No se pudo precargar el snapshot al arrancar; se llenará con las primeras consultas", ex);
        }
    }
}
