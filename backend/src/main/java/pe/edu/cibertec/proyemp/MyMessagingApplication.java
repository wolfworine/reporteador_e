package pe.edu.cibertec.proyemp;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

import java.util.stream.Stream;

/**
 * Demo generado por el codestart de la extensión messaging-kafka, dejado
 * intacto para probar que la extensión smallrye-reactive-messaging-kafka
 * (mapeada a quarkus-messaging-kafka en Quarkus 3.38.x, ver
 * progress/history.md) queda correctamente cableada en el bootstrap. No es
 * lógica de dominio y se reemplazará/eliminará cuando la feature 7
 * (reportes_generacion_async_kafka) introduzca los canales reales.
 */
@ApplicationScoped
public class MyMessagingApplication {

    private static final Logger LOG = Logger.getLogger(MyMessagingApplication.class);

    /**
     * Injects an emitter to send messages to the "words-out" channel.
     */
    @Channel("words-out")
    Emitter<String> emitter;

    /**
     * Sends message to the "words-out" channel, can be used from a JAX-RS resource or any bean of your application.
     * Messages are sent to the broker.
     **/
    void onStart(@Observes StartupEvent ev) {
        Stream.of("Hello", "with", "Quarkus", "Messaging", "message").forEach(string -> emitter.send(string));
    }

    /**
     * Consume the message from the "words-in" channel, uppercase it and send it to the uppercase channel.
     * This method is called by the framework when a message is received on the "words-in" channel (from the broker).
     **/
    @Incoming("words-in")
    @Outgoing("uppercase")
    public String toUpperCase(String message) {
        return message.toUpperCase();
    }

    /**
     * Consume the uppercase channel (coming from within the application) and print the messages to the console.
     **/
    @Incoming("uppercase")
    public void sink(String word) {
        LOG.infof(">> %s", word);
    }
}
