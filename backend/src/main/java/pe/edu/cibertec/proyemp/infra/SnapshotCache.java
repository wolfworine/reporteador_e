package pe.edu.cibertec.proyemp.infra;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.jboss.logging.Logger;
import pe.edu.cibertec.proyemp.domain.InfraestructuraNoDisponibleException;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Snapshot en memoria de lecturas a DynamoDB, con dos ventanas:
 *
 * <ul>
 *   <li><b>ttl</b> ({@code app.snapshot.ttl}): mientras el snapshot sea más
 *       joven que esto se sirve sin tocar la base (consultas instantáneas).
 *       {@code PT0S} desactiva el caché "fresco" (lo usa el perfil
 *       {@code %test}).</li>
 *   <li><b>respaldo ante caída</b> (espera máxima {@code app.snapshot.max-wait-with-backup}, 2 s, a la base si hay respaldo) ({@code app.snapshot.stale-if-error}): si
 *       al recargar DynamoDB falla (error del SDK, timeout o circuito
 *       abierto), se sigue sirviendo el último snapshot durante
 *       {@code ttl + stale-if-error} desde que se cargó -- es decir, quien lo
 *       cargó justo antes de la caída tiene como mínimo
 *       {@code stale-if-error} (2 min por defecto) de servicio degradado.
 *       Pasado ese límite, el fallo se propaga como
 *       {@link InfraestructuraNoDisponibleException} (503).</li>
 * </ul>
 *
 * <p>Las cargas concurrentes de la misma clave se comparten (una sola ida a
 * la base, sin estampida). Solo se cachean éxitos: un "no encontrado" no
 * entra al snapshot. {@link #invalidar} marca las entradas como vencidas pero
 * conserva el valor como respaldo ante caída, y sube la {@link #generacion()}
 * que usa la idempotencia de reportes para no devolver un reporte construido
 * con datos anteriores a una escritura.
 *
 * <p>Es memoria por instancia: con varias réplicas cada una tiene su copia,
 * el {@code ttl} corto acota la desactualización entre ellas.
 */
@ApplicationScoped
public class SnapshotCache {

    private static final Logger LOG = Logger.getLogger(SnapshotCache.class);

    private record Entrada(Object valor, long cargadoEn, boolean invalidada) {
    }

    private final LecturaResiliente lectura;
    private final long ttlMs;
    private final long respaldoMs;
    private final Duration esperaMaxConRespaldo;
    private final LongSupplier reloj;
    private final ConcurrentHashMap<String, Entrada> entradas = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Uni<?>> cargasEnCurso = new ConcurrentHashMap<>();
    private final AtomicLong generacion = new AtomicLong();

    @Inject
    public SnapshotCache(LecturaResiliente lectura,
            @ConfigProperty(name = "app.snapshot.ttl", defaultValue = "PT60S") Duration ttl,
            @ConfigProperty(name = "app.snapshot.stale-if-error", defaultValue = "PT2M") Duration respaldoAnteError,
            @ConfigProperty(name = "app.snapshot.max-wait-with-backup", defaultValue = "PT2S") Duration esperaMaxConRespaldo) {
        this(lectura, ttl, respaldoAnteError, esperaMaxConRespaldo, System::currentTimeMillis);
    }

    SnapshotCache(LecturaResiliente lectura, Duration ttl, Duration respaldoAnteError,
            Duration esperaMaxConRespaldo, LongSupplier reloj) {
        this.esperaMaxConRespaldo = esperaMaxConRespaldo;
        this.lectura = lectura;
        this.ttlMs = ttl.toMillis();
        this.respaldoMs = respaldoAnteError.toMillis();
        this.reloj = reloj;
    }

    @SuppressWarnings("unchecked")
    public <T> Uni<T> obtener(String clave, Supplier<Uni<T>> carga) {
        Entrada entrada = entradas.get(clave);
        if (entrada != null && !entrada.invalidada() && edad(entrada) < ttlMs) {
            return Uni.createFrom().item((T) entrada.valor());
        }
        Uni<T> recarga = cargarCompartido(clave, carga);
        if (entrada != null && edad(entrada) <= ttlMs + respaldoMs) {
            // Hay respaldo utilizable: no se espera a una base lenta o caida
            // (timeouts+reintentos pueden sumar decenas de segundos). La carga
            // compartida sigue en segundo plano y refresca el snapshot sola.
            recarga = recarga.ifNoItem().after(esperaMaxConRespaldo).fail();
        }
        return recarga
                .onFailure(SnapshotCache::esFalloDeInfraestructura)
                .recoverWithUni(fallo -> respaldoOFallo(clave, fallo));
    }

    /** Marca como vencidas las entradas cuya clave empieza con {@code prefijo} y sube la generación. */
    public void invalidar(String prefijo) {
        generacion.incrementAndGet();
        entradas.replaceAll((clave, entrada) -> clave.startsWith(prefijo) && !entrada.invalidada()
                ? new Entrada(entrada.valor(), entrada.cargadoEn(), true)
                : entrada);
    }

    /** Contador que sube con cada escritura que invalida el snapshot. */
    public long generacion() {
        return generacion.get();
    }

    @SuppressWarnings("unchecked")
    private <T> Uni<T> cargarCompartido(String clave, Supplier<Uni<T>> carga) {
        return (Uni<T>) cargasEnCurso.computeIfAbsent(clave, k -> lectura.ejecutar(carga)
                .onItem().invoke(valor -> entradas.put(k, new Entrada(valor, reloj.getAsLong(), false)))
                .onTermination().invoke(() -> cargasEnCurso.remove(k))
                .memoize().indefinitely());
    }

    @SuppressWarnings("unchecked")
    private <T> Uni<T> respaldoOFallo(String clave, Throwable fallo) {
        if (!(fallo instanceof CircuitBreakerOpenException)) {
            LOG.warnf(fallo, "Fallo leyendo '%s' de la base de datos", clave);
        }
        Entrada entrada = entradas.get(clave);
        if (entrada != null && edad(entrada) <= ttlMs + respaldoMs) {
            LOG.warnf("Base de datos no disponible para '%s' (%s): sirviendo snapshot de hace %d s",
                    clave, fallo.getClass().getSimpleName(), edad(entrada) / 1000);
            return Uni.createFrom().item((T) entrada.valor());
        }
        return Uni.createFrom().failure(fallo instanceof InfraestructuraNoDisponibleException
                ? fallo
                : new InfraestructuraNoDisponibleException(clave, fallo));
    }

    private long edad(Entrada entrada) {
        return reloj.getAsLong() - entrada.cargadoEn();
    }

    static boolean esFalloDeInfraestructura(Throwable fallo) {
        return fallo instanceof SdkException
                || fallo instanceof TimeoutException
                || fallo instanceof io.smallrye.mutiny.TimeoutException
                || fallo instanceof CircuitBreakerOpenException
                || fallo instanceof InfraestructuraNoDisponibleException;
    }
}
