package pe.edu.cibertec.proyemp.web;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.proyemp.domain.InfraestructuraNoDisponibleException;
import software.amazon.awssdk.core.exception.SdkException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature 12 ({@code reactive_resilience_observability}) -- test unitario
 * puro (sin Quarkus) de {@link UniFallosInfraestructura}, el helper que
 * aplica {@code onFailure()} explícito en {@code web/ReporteGenerarResource}
 * y {@code web/ReporteResource}.
 */
class UniFallosInfraestructuraTest {

    @Test
    void should_wrapIntoInfraestructuraNoDisponible_when_sdkExceptionFails() {
        Uni<String> falloSdk = Uni.createFrom().failure(SdkException.create("boom", null));

        UniAssertSubscriber<String> subscriber = UniFallosInfraestructura
                .manejarFallosInfraestructura(falloSdk, "contexto-dynamodb")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.assertFailedWith(InfraestructuraNoDisponibleException.class, "contexto-dynamodb");
    }

    @Test
    void should_wrapIntoInfraestructuraNoDisponible_when_kafkaExceptionFails() {
        Uni<String> falloKafka = Uni.createFrom().failure(new KafkaException("broker down"));

        UniAssertSubscriber<String> subscriber = UniFallosInfraestructura
                .manejarFallosInfraestructura(falloKafka, "contexto-kafka")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.assertFailedWith(InfraestructuraNoDisponibleException.class, "contexto-kafka");
    }

    @Test
    void should_letOtherFailuresPassThroughUnwrapped_when_failureIsNotInfrastructureRelated() {
        IllegalStateException fallosDeBug = new IllegalStateException("bug real, no reintentable");
        Uni<String> falloDominio = Uni.createFrom().failure(fallosDeBug);

        UniAssertSubscriber<String> subscriber = UniFallosInfraestructura
                .manejarFallosInfraestructura(falloDominio, "no deberia usarse")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.assertFailedWith(IllegalStateException.class, "bug real, no reintentable");
    }

    @Test
    void should_classifyOnlySdkAndKafkaExceptionsAsInfrastructureFailures() {
        assertTrue(UniFallosInfraestructura.esFalloDeInfraestructura(SdkException.create("x", null)));
        assertTrue(UniFallosInfraestructura.esFalloDeInfraestructura(new KafkaException("x")));
        assertFalse(UniFallosInfraestructura.esFalloDeInfraestructura(new IllegalStateException("x")));
        assertFalse(UniFallosInfraestructura.esFalloDeInfraestructura(new NullPointerException()));
    }
}
