package pe.edu.cibertec.proyemp.messaging;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Puente per-proceso entre {@code messaging/ReporteRequestWorker} (feature 9,
 * consumidor real de Kafka) y el endpoint SSE {@code GET /api/reportes/eventos}
 * (feature 10, {@code web/ReporteResource}).
 *
 * <p><b>Por qué existe (docs/kafka-reactive-guidance.md §"SSE — relación con
 * Kafka"):</b> el endpoint SSE NO debe consumir Kafka directamente por
 * request -- eso crearía un consumer group nuevo por cada conexión SSE
 * abierta, ineficiente y sin necesidad. En vez de eso, {@code ReporteRequestWorker}
 * (que sí es el único consumidor real de {@code report.requested}/publicador
 * de {@code report.ready}/{@code report.failed}) alimenta este
 * {@link BroadcastProcessor} interno cada vez que termina de procesar un
 * reporte; múltiples clientes SSE conectados a este mismo proceso comparten
 * el mismo {@link Multi} vía {@link #stream()}.
 *
 * <p><b>Limitación conocida (documentada, no resuelta aquí):</b> este
 * {@link BroadcastProcessor} vive en memoria de este proceso. Si el backend
 * escala a múltiples instancias, un cliente SSE conectado a la instancia A
 * no vería un evento generado por la instancia B -- fuera de scope de esta
 * feature (docs/kafka-reactive-guidance.md ya lo documenta como limitación
 * conocida a resolver si/cuando se decida escalar horizontalmente).
 */
@ApplicationScoped
public class ReporteEventoBroadcaster {

    private final BroadcastProcessor<ReporteEvento> processor = BroadcastProcessor.create();

    /** Llamado por {@code ReporteRequestWorker} tras publicar report.ready/report.failed. */
    public void emitir(ReporteEvento evento) {
        processor.onNext(evento);
    }

    /** Expuesto directo por {@code web/ReporteResource#eventos()} -- multicast a todos los suscriptores conectados. */
    public Multi<ReporteEvento> stream() {
        return processor;
    }
}
