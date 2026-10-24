package pe.edu.cibertec.proyemp.web;

import io.smallrye.mutiny.Uni;
import org.apache.kafka.common.KafkaException;
import pe.edu.cibertec.proyemp.domain.InfraestructuraNoDisponibleException;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * Helper reutilizado por los endpoints "principales" (reportes y escritura
 * POST/PUT) para el manejo explícito de fallos de {@code Uni} pedido por la
 * feature 12 ({@code reactive_resilience_observability}): "Manejo explícito
 * de fallos de Uni (onFailure) en los endpoints principales".
 *
 * <p>Solo intercepta fallos que representan una dependencia externa
 * (DynamoDB/{@link SdkException}, Kafka/{@link KafkaException}) no
 * disponible -- cualquier otro tipo de excepción (un bug real, ej. un
 * {@code NullPointerException}) se deja pasar sin envolver, para no
 * disfrazar un error de programación como "reintente en unos segundos" (503).
 * Las excepciones de dominio propias (ej. {@code CatalogoNoEncontradoException},
 * {@code ReporteNoListoException}) tampoco matchean el predicado, así que
 * siguen su propio {@code @ServerExceptionMapper} sin pasar por acá.
 *
 * <p>El resultado ({@link InfraestructuraNoDisponibleException}) lo mapea
 * {@code web/exception/InfraestructuraExceptionMappers} a HTTP 503 con un
 * mensaje claro -- esto cubre el caso "endpoint de escritura ante fallo de
 * DynamoDB/Kafka" del acceptance 3 para los endpoints donde se aplica
 * explícitamente. El resto de los endpoints de escritura (catálogos
 * simples, rol/suministro/atributo, usuario, menú) quedan cubiertos por el
 * mismo mapper de forma centralizada -- cualquier {@link SdkException} sin
 * envolver que llegue hasta RESTEasy Reactive también se mapea a 503 ahí
 * (ver Javadoc de esa clase), sin necesidad de repetir este wrapper en cada
 * uno de los ~10 recursos REST de catálogos/dominio (evita boilerplate
 * idéntico repetido, docs/conventions.md no exige un patrón per-endpoint
 * cuando uno centralizado ya cubre el mismo caso sin perder claridad).
 */
public final class UniFallosInfraestructura {

    private UniFallosInfraestructura() {
    }

    public static boolean esFalloDeInfraestructura(Throwable failure) {
        return failure instanceof SdkException || failure instanceof KafkaException;
    }

    /**
     * Envuelve los fallos de infraestructura del {@code Uni} dado en
     * {@link InfraestructuraNoDisponibleException}, dejando pasar sin tocar
     * cualquier otro tipo de fallo (excepciones de dominio ya mapeadas
     * aparte, o bugs reales que deben seguir siendo 500).
     */
    public static <T> Uni<T> manejarFallosInfraestructura(Uni<T> uni, String contexto) {
        return uni.onFailure(UniFallosInfraestructura::esFalloDeInfraestructura)
                .transform(failure -> new InfraestructuraNoDisponibleException(contexto, failure));
    }
}
