package pe.edu.cibertec.proyemp.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import pe.edu.cibertec.proyemp.domain.EstadoReporte;
import pe.edu.cibertec.proyemp.domain.Reporte;
import pe.edu.cibertec.proyemp.domain.ReporteEjecucion;
import pe.edu.cibertec.proyemp.report.ReportGeneratorPort;
import pe.edu.cibertec.proyemp.repository.ReporteEjecucionRepository;
import pe.edu.cibertec.proyemp.service.ReporteService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Worker Kafka de la feature 9 ({@code reportes_generacion_async_kafka}):
 * {@code @Incoming("report-requested-in")} consume {@code report.requested},
 * reutiliza {@code service/ReporteService} (feature 7, ensamblado) y
 * {@code report/ReportGeneratorPort} {@code @Named("pdf")}/{@code @Named("excel")}
 * (feature 8, generación binaria) sin duplicar su lógica, persiste el
 * resultado vía {@link ReporteEjecucionRepository} y publica
 * {@code report.ready}/{@code report.failed}.
 *
 * <p><b>No bloquea el hilo del broker:</b> todo el pipeline es
 * {@code Uni} de punta a punta (acceso DynamoDB vía
 * {@code DynamoDbAsyncClient} envuelto, generación PDF/Excel ya offloadeada
 * a worker pool dentro de sus propios adaptadores, feature 8) -- este
 * worker no agrega ningún {@code .await()} ni trabajo bloqueante propio.
 *
 * <p><b>Puente SSE (feature 10, agregado sin tocar la lógica de arriba):</b>
 * tras confirmar la publicación de {@code report.ready}/{@code report.failed}
 * en Kafka, este worker también alimenta {@link ReporteEventoBroadcaster}
 * (ver su Javadoc para el porqué) para que
 * {@code GET /api/reportes/eventos} notifique a los clientes SSE conectados
 * sin crear un consumer group nuevo por conexión
 * (docs/kafka-reactive-guidance.md §"SSE — relación con Kafka"). Es un
 * side-effect puramente aditivo: no cambia el dedupe, los reintentos ni la
 * persistencia ya aprobados en la feature 9.
 *
 * <h2>Garantías de entrega (docs/kafka-reactive-guidance.md)</h2>
 * <ul>
 *   <li><b>Dedupe ante redelivery:</b> antes de generar, se lee el
 *   {@code estado} persistido de la {@link ReporteEjecucion}; si ya es
 *   {@code LISTO} o {@code ERROR}, no se regenera -- solo se completa el
 *   {@code Uni} (ack). El productor tiene {@code acks=all} +
 *   {@code enable.idempotence=true} explícitos
 *   (application.properties), y aun así un mensaje puede llegar duplicado
 *   por otras causas (reintento de aplicación, rebalance) -- este chequeo
 *   es la defensa real.</li>
 *   <li><b>Sin auto-commit:</b> el canal {@code report-requested-in} usa
 *   {@code commit-strategy=throttled} (no {@code latest}) -- el offset
 *   avanza solo después de que este método completa exitosamente su
 *   {@code Uni}.</li>
 *   <li><b>Poison pill:</b> reintento local acotado a {@link #MAX_INTENTOS}
 *   intentos, contado por el atributo {@code intentos} PERSISTIDO en
 *   DynamoDB (no una variable local) -- sobrevive a un crash-restart del
 *   worker a media generación. Agotados los reintentos: {@code estado=ERROR}
 *   + {@code motivoError} + publica {@code report.failed}, y el método
 *   igual completa exitosamente (el offset avanza, la partición no se
 *   atasca). Un fallo de infraestructura real (ej. DynamoDB/Kafka caídos al
 *   momento de persistir el resultado) sí se propaga sin capturar --ahí no
 *   se hace commit, y es correcto que Kafka reintregue el mensaje.</li>
 *   <li><b>Partición:</b> tanto {@code report.requested} (publicado por
 *   {@code web/ReporteGenerarResource}) como {@code report.ready}/
 *   {@code report.failed} (publicados aquí) usan el {@code id} del reporte
 *   como key -- mismo reporte, misma partición, orden garantizado.</li>
 * </ul>
 */
@ApplicationScoped
public class ReporteRequestWorker {

    private static final Logger LOG = Logger.getLogger(ReporteRequestWorker.class);
    private static final int MAX_INTENTOS = 3;

    private final ReporteService reporteService;
    private final ReporteEjecucionRepository repository;
    private final ReportGeneratorPort pdfAdapter;
    private final ReportGeneratorPort excelAdapter;
    private final ObjectMapper objectMapper;
    private final Emitter<String> reportReadyEmitter;
    private final Emitter<String> reportFailedEmitter;
    private final ReporteEventoBroadcaster eventoBroadcaster;

    @Inject
    public ReporteRequestWorker(ReporteService reporteService, ReporteEjecucionRepository repository,
            @Named("pdf") ReportGeneratorPort pdfAdapter, @Named("excel") ReportGeneratorPort excelAdapter,
            ObjectMapper objectMapper,
            @Channel("report-ready-out") Emitter<String> reportReadyEmitter,
            @Channel("report-failed-out") Emitter<String> reportFailedEmitter,
            ReporteEventoBroadcaster eventoBroadcaster) {
        this.reporteService = reporteService;
        this.repository = repository;
        this.pdfAdapter = pdfAdapter;
        this.excelAdapter = excelAdapter;
        this.objectMapper = objectMapper;
        this.reportReadyEmitter = reportReadyEmitter;
        this.reportFailedEmitter = reportFailedEmitter;
        this.eventoBroadcaster = eventoBroadcaster;
    }

    @Incoming("report-requested-in")
    public Uni<Void> onReportRequested(String payload) {
        ReportRequestedEvent evento = deserializar(payload, ReportRequestedEvent.class);
        return repository.buscarPorId(evento.reporteId())
                .chain(ejecucion -> {
                    if (ejecucion.estado() != EstadoReporte.PENDIENTE) {
                        LOG.infof(
                                "Reporte %s ya esta en estado %s -- ignorando redelivery de report.requested, no se regenera",
                                evento.reporteId(), ejecucion.estado());
                        return Uni.createFrom().voidItem();
                    }
                    return procesar(evento);
                });
    }

    private Uni<Void> procesar(ReportRequestedEvent evento) {
        return repository.incrementarIntentos(evento.reporteId())
                .chain(intentos -> generarYPersistir(evento)
                        .onFailure().recoverWithUni(failure -> {
                            LOG.warnf(failure, "Intento %d/%d fallido generando el reporte %s", intentos,
                                    MAX_INTENTOS, evento.reporteId());
                            if (intentos >= MAX_INTENTOS) {
                                return marcarErrorYPublicarFailed(evento.reporteId(), failure);
                            }
                            return procesar(evento);
                        }));
    }

    private Uni<Void> generarYPersistir(ReportRequestedEvent evento) {
        return reporteService.generar(evento.idAplicacion(), evento.idArea())
                .chain(reportes -> seleccionarGenerador(evento.formato()).generar(reportes)
                        .chain(binario -> repository.marcarListo(evento.reporteId(), binario,
                                serializar(reportes))))
                .chain(ignored -> publicarReady(evento.reporteId()));
    }

    private Uni<Void> marcarErrorYPublicarFailed(String reporteId, Throwable failure) {
        String motivo = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
        return repository.marcarError(reporteId, motivo)
                .chain(ignored -> publicarFailed(reporteId, motivo));
    }

    private ReportGeneratorPort seleccionarGenerador(String formato) {
        if ("excel".equalsIgnoreCase(formato)) {
            return excelAdapter;
        }
        return pdfAdapter;
    }

    private Uni<Void> publicarReady(String reporteId) {
        String json = serializar(new ReportReadyEvent(reporteId));
        CompletableFuture<Void> ackFuture = new CompletableFuture<>();
        reportReadyEmitter.send(mensajeConKey(reporteId, json, ackFuture));
        return Uni.createFrom().completionStage(ackFuture)
                .invoke(() -> emitirEventoSeguro(ReporteEvento.listo(reporteId)));
    }

    private Uni<Void> publicarFailed(String reporteId, String motivo) {
        String json = serializar(new ReportFailedEvent(reporteId, motivo));
        CompletableFuture<Void> ackFuture = new CompletableFuture<>();
        reportFailedEmitter.send(mensajeConKey(reporteId, json, ackFuture));
        return Uni.createFrom().completionStage(ackFuture)
                .invoke(() -> emitirEventoSeguro(ReporteEvento.fallido(reporteId, motivo)));
    }

    /**
     * Nota no bloqueante heredada de la review de la feature 11, resuelta en
     * la feature 12 ({@code reactive_resilience_observability}): el puente
     * SSE ({@link ReporteEventoBroadcaster#emitir}) es un side-effect
     * puramente aditivo (feature 10) sobre el resultado YA CONFIRMADO en
     * Kafka/DynamoDB (el {@code ackFuture} de {@code report.ready}/
     * {@code report.failed} ya completó antes de llegar acá). Si
     * {@code emitir(...)} lanzara una excepción (hoy no debería, ver Javadoc
     * de {@link ReporteEventoBroadcaster}, pero no hay garantía en tiempo de
     * compilación de que siga siendo así para siempre), un {@code .invoke()}
     * sin este try/catch propagaría esa falla al {@code Uni<Void>} que
     * devuelve {@code publicarReady}/{@code publicarFailed}, y
     * {@link #procesar} la interpretaría como un fallo real de generación
     * del reporte -- reintentando o marcando {@code ERROR} un reporte que en
     * realidad ya se generó y persistió correctamente. Se captura y loguea
     * en su lugar, sin dejarla contaminar el resultado real del worker
     * (docs/conventions.md prohíbe tragar excepciones en silencio -- por eso
     * se loguea en {@code WARN}, no se descarta sin rastro).
     */
    private void emitirEventoSeguro(ReporteEvento evento) {
        try {
            eventoBroadcaster.emitir(evento);
        } catch (RuntimeException ex) {
            LOG.warnf(ex,
                    "Fallo al emitir el evento SSE %s para el reporte %s -- ignorado, no afecta el resultado ya confirmado en Kafka/DynamoDB",
                    evento.estado(), evento.id());
        }
    }

    /**
     * {@code Emitter<String>.send(Message)} es {@code void} en esta version
     * de MicroProfile Reactive Messaging -- se usa {@code withAck}/
     * {@code withNack} del propio {@link Message} para completar un
     * {@link CompletableFuture} propio y esperar la confirmacion real de
     * publicacion (coherente con {@code acks=all}), igual que
     * {@code web/ReporteGenerarResource#publicarRequested}.
     */
    private static Message<String> mensajeConKey(String key, String json, CompletableFuture<Void> ackFuture) {
        return Message.of(json)
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder().withKey(key).build())
                .withAck(() -> {
                    ackFuture.complete(null);
                    return CompletableFuture.completedFuture(null);
                })
                .withNack(failure -> {
                    ackFuture.completeExceptionally(failure);
                    return CompletableFuture.completedFuture(null);
                });
    }

    private String serializar(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception ex) {
            throw new IllegalStateException("Error serializando payload de mensajeria de reportes", ex);
        }
    }

    private <T> T deserializar(String json, Class<T> tipo) {
        try {
            return objectMapper.readValue(json, tipo);
        } catch (Exception ex) {
            throw new IllegalStateException("Error deserializando payload de mensajeria de reportes", ex);
        }
    }
}
