package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.proyemp.messaging.ReporteEvento;
import pe.edu.cibertec.proyemp.messaging.ReporteEventoBroadcaster;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature 12 ({@code reactive_resilience_observability}), acceptance 4: "El
 * endpoint SSE cierra la conexión limpiamente sin excepción no controlada
 * cuando el cliente se desconecta (test que simula la desconexión)".
 *
 * <p>Se suscribe directo al {@link io.smallrye.mutiny.Multi} devuelto por
 * {@link ReporteResource#eventos()} (el mismo objeto que RESTEasy Reactive
 * expone en {@code GET /api/reportes/eventos}, incluidos los operadores
 * {@code onCancellation()}/{@code onFailure()} agregados en esta feature) y
 * cancela la suscripción -- eso es exactamente lo que hace RESTEasy Reactive
 * cuando el cliente HTTP cierra la conexión de un stream SSE, sin necesidad
 * de simular la desconexión a nivel de socket real. Confirma:
 * <ol>
 *   <li>{@link AssertSubscriber#cancel()} no lanza ninguna excepción (la
 *   propia aserción de JUnit fallaría si algo se propagara sin control).</li>
 *   <li>El broadcaster sigue sano después: un nuevo suscriptor conectado
 *   DESPUÉS de la cancelación del anterior sigue recibiendo eventos con
 *   normalidad -- prueba que la desconexión de un cliente no corrompe el
 *   {@code BroadcastProcessor} compartido para el resto de conexiones
 *   activas.</li>
 * </ol>
 */
@QuarkusTest
class ReporteResourceSseDisconnectTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Inject
    ReporteResource reporteResource;

    @Inject
    ReporteEventoBroadcaster eventoBroadcaster;

    @Test
    void should_closeCleanlyWithoutUncontrolledException_when_clientDisconnectsFromSseStream() {
        AssertSubscriber<ReporteEvento> subscriber = reporteResource.eventos()
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        subscriber.assertSubscribed();

        // Simula la desconexión del cliente: RESTEasy Reactive cancela la
        // suscripción al Multi exactamente así cuando el socket SSE se
        // cierra del lado del cliente.
        subscriber.cancel();

        assertTrue(subscriber.isCancelled(), "la suscripcion deberia quedar cancelada limpiamente");
        assertNull(subscriber.getFailure(),
                "la desconexion del cliente no deberia dejar una excepcion no controlada en el subscriber");
    }

    @Test
    void should_keepBroadcasterHealthyForOtherClients_when_oneClientDisconnects() {
        AssertSubscriber<ReporteEvento> clienteQueSeDesconecta = reporteResource.eventos()
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        clienteQueSeDesconecta.cancel();

        // Un segundo cliente se conecta DESPUES de que el primero se
        // desconecto -- el stream compartido no deberia haberse roto.
        AssertSubscriber<ReporteEvento> clienteSano = reporteResource.eventos()
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));

        ReporteEvento evento = ReporteEvento.listo("id-" + UUID.randomUUID());
        eventoBroadcaster.emitir(evento);

        clienteSano.awaitItems(1, TIMEOUT);
        clienteSano.assertItems(evento);
        assertFalse(clienteSano.isCancelled());

        clienteSano.cancel();
    }
}
