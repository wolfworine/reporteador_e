package pe.edu.cibertec.proyemp.infra;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

/**
 * Frontera de resiliencia para las LECTURAS a DynamoDB (SmallRye Fault
 * Tolerance, estándar Quarkus -- no Spring/Resilience4j, ver
 * docs/conventions.md). Todas las lecturas cacheadas de {@link SnapshotCache}
 * pasan por {@link #ejecutar}, así comparten un único circuit breaker: hay
 * una sola base de datos, si cae, cae para todas.
 *
 * <p>Orden de ejecución de SmallRye FT: Retry envuelve a CircuitBreaker, que
 * envuelve a Timeout -- cada intento reintentado cuenta contra el breaker, y
 * con el circuito abierto los reintentos se cortan de inmediato
 * ({@code CircuitBreakerOpenException} no está en {@code retryOn}).
 *
 * <p>Solo cuentan como fallo los errores de infraestructura
 * ({@link SdkException}, {@link TimeoutException}). Las excepciones de
 * dominio (ej. {@code CatalogoNoEncontradoException}) no reintentan ni abren
 * el circuito. No se aplica a escrituras: reintentar un PUT no idempotente
 * podría duplicar datos; ellas siguen devolviendo 503 vía
 * {@code UniFallosInfraestructura}.
 */
@ApplicationScoped
public class LecturaResiliente {

    // 10 s: el primer acceso en frio (cliente AWS/Netty recien creado) es lento; un
    // timeout corto lo contaria como fallo y abriria el circuito al arrancar.
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 200, jitter = 100, retryOn = {SdkException.class, TimeoutException.class})
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10, delayUnit = ChronoUnit.SECONDS,
            successThreshold = 2, failOn = {SdkException.class, TimeoutException.class})
    public <T> Uni<T> ejecutar(Supplier<Uni<T>> lectura) {
        return lectura.get();
    }
}
