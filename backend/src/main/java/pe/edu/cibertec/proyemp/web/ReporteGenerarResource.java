package pe.edu.cibertec.proyemp.web;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.edu.cibertec.proyemp.domain.EstadoReporte;
import pe.edu.cibertec.proyemp.infra.SnapshotCache;
import pe.edu.cibertec.proyemp.messaging.ReportRequestedEvent;
import pe.edu.cibertec.proyemp.repository.ReporteEjecucionRepository;
import pe.edu.cibertec.proyemp.web.dto.ReporteGenerarRequest;
import pe.edu.cibertec.proyemp.web.dto.ReporteGenerarResponse;

import static pe.edu.cibertec.proyemp.web.UniFallosInfraestructura.manejarFallosInfraestructura;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Único punto de entrada público para generar reportes (feature 9,
 * {@code reportes_generacion_async_kafka}) -- 100% asíncrono, nunca
 * devuelve el binario en esta respuesta (acceptance 1,
 * docs/architecture.md §"Reportes: asíncrono de punta a punta").
 *
 * <p>Orden estricto: persiste la fila {@code PENDIENTE} en DynamoDB
 * ({@link ReporteEjecucionRepository#crearPendiente}) ANTES de publicar
 * {@code report.requested} -- si se publicara primero, el worker podría
 * consumir el mensaje y hacer {@code buscarPorId} antes de que la fila
 * exista. El {@code id} (UUID) es la clave de partición Kafka del mensaje
 * (mismo reporte, misma partición, orden {@code requested}-&gt;
 * {@code ready}/{@code failed} garantizado).
 */
@Path("/api/reportes")
public class ReporteGenerarResource {

    private final ReporteEjecucionRepository repository;
    private final ObjectMapper objectMapper;
    private final Emitter<String> reportRequestedEmitter;
    private final SnapshotCache snapshot;
    private final long ventanaIdempotenciaMs;

    @Inject
    public ReporteGenerarResource(ReporteEjecucionRepository repository, ObjectMapper objectMapper,
            @Channel("report-requested-out") Emitter<String> reportRequestedEmitter, SnapshotCache snapshot,
            @ConfigProperty(name = "app.reporte.idempotencia-ventana", defaultValue = "PT5M") Duration ventana) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.reportRequestedEmitter = reportRequestedEmitter;
        this.snapshot = snapshot;
        this.ventanaIdempotenciaMs = ventana.toMillis();
    }

    @POST
    @Path("/generar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> generar(ReporteGenerarRequest request) {
        String formato = request.formatoNormalizado();
        String id = idDeterministico(request.idAplicacion(), request.idArea(), formato);

        // Idempotencia: la fila se crea con escritura condicional. Si otra
        // petición idéntica (misma app/área/formato, misma ventana, mismos
        // datos) ya la creó, NO se publica a Kafka ni se vuelve a generar --
        // se devuelve el mismo id (PENDIENTE: ya viene en camino; LISTO: ya
        // está generado). Solo un reporte en ERROR se reintenta con id nuevo.
        Uni<Response> resultado = repository.crearPendienteSiNoExiste(id, request.idAplicacion(), request.idArea(),
                        formato)
                .chain(creado -> creado
                        ? publicarRequested(id, request.idAplicacion(), request.idArea(), formato).replaceWith(id)
                        : reutilizarOReintentar(id, request.idAplicacion(), request.idArea(), formato))
                .onItem().transform(idFinal -> Response.status(Response.Status.ACCEPTED)
                        .entity(new ReporteGenerarResponse(idFinal))
                        .build());
        // Manejo explícito de fallos de Uni (feature 12,
        // reactive_resilience_observability, acceptance 3): un fallo de
        // DynamoDB (crearPendiente) o Kafka (publicarRequested, vía el nack
        // del broker propagado por el ackFuture) se convierte en un 503
        // claro en vez del 500 genérico por defecto -- ver Javadoc de
        // UniFallosInfraestructura.
        return manejarFallosInfraestructura(resultado, "POST /api/reportes/generar id=" + id);
    }

    /**
     * UUID derivado (v3) de app|área|formato|versión-de-datos|ventana-de-tiempo.
     * La versión de datos es {@link SnapshotCache#generacion()}: cualquier
     * escritura de catálogos/roles/suministros/atributos la sube, así que un
     * reporte pedido después de modificar datos nunca reutiliza uno armado
     * con los datos anteriores. La ventana (ver
     * {@code app.reporte.idempotencia-ventana}, {@code PT0S} = sin
     * idempotencia) acota cuánto se reutiliza un mismo reporte.
     */
    private String idDeterministico(String idAplicacion, String idArea, String formato) {
        if (ventanaIdempotenciaMs <= 0) {
            return UUID.randomUUID().toString();
        }
        String clave = idAplicacion + "|" + idArea + "|" + formato + "|" + snapshot.generacion() + "|"
                + System.currentTimeMillis() / ventanaIdempotenciaMs;
        return UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Uni<String> reutilizarOReintentar(String id, String idAplicacion, String idArea, String formato) {
        return repository.buscarPorId(id).chain(existente -> {
            if (existente.estado() != EstadoReporte.ERROR) {
                return Uni.createFrom().item(id);
            }
            String idNuevo = UUID.randomUUID().toString();
            return repository.crearPendiente(idNuevo, idAplicacion, idArea, formato)
                    .chain(() -> publicarRequested(idNuevo, idAplicacion, idArea, formato))
                    .replaceWith(idNuevo);
        });
    }

    /**
     * {@code Emitter<String>.send(Message)} de esta version de MicroProfile
     * Reactive Messaging es {@code void} -- no devuelve directamente un
     * {@code CompletionStage} para esperar el ack del broker. Se usa el
     * callback {@code withAck}/{@code withNack} del propio {@link Message}
     * (API estandar) para completar un {@link CompletableFuture} propio y
     * envolverlo en {@code Uni}, así el llamador espera la confirmación real
     * de publicacion (coherente con {@code acks=all} configurado en
     * application.properties) antes de responder {@code 202 Accepted}.
     */
    private Uni<Void> publicarRequested(String id, String idAplicacion, String idArea, String formato) {
        String json = serializar(new ReportRequestedEvent(id, idAplicacion, idArea, formato));
        CompletableFuture<Void> ackFuture = new CompletableFuture<>();
        Message<String> mensaje = Message.of(json)
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder().withKey(id).build())
                .withAck(() -> {
                    ackFuture.complete(null);
                    return CompletableFuture.completedFuture(null);
                })
                .withNack(failure -> {
                    ackFuture.completeExceptionally(failure);
                    return CompletableFuture.completedFuture(null);
                });
        reportRequestedEmitter.send(mensaje);
        return Uni.createFrom().completionStage(ackFuture);
    }

    private String serializar(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception ex) {
            throw new IllegalStateException("Error serializando ReportRequestedEvent", ex);
        }
    }
}
