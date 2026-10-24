package pe.edu.cibertec.proyemp.infra;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.proyemp.domain.CatalogoNoEncontradoException;
import pe.edu.cibertec.proyemp.domain.InfraestructuraNoDisponibleException;
import pe.edu.cibertec.proyemp.domain.TipoCatalogo;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit test puro (sin Quarkus, reloj controlado) de {@link SnapshotCache}:
 * ttl de 60 s + respaldo ante caída de 2 min, el comportamiento que se pidió
 * ("si cae la BD, el snapshot sigue disponible 2 minutos").
 */
class SnapshotCacheTest {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration RESPALDO = Duration.ofMinutes(2);

    private final AtomicLong ahora = new AtomicLong(1_000_000);
    private final AtomicInteger llamadas = new AtomicInteger();

    /** Sin FT: ejecuta la lectura directo (las anotaciones solo actúan vía CDI). */
    private final SnapshotCache cache = new SnapshotCache(new LecturaResiliente(), TTL, RESPALDO,
            Duration.ofMillis(300), ahora::get);

    private Supplier<Uni<String>> lecturaOk(String valor) {
        return () -> Uni.createFrom().item(() -> {
            llamadas.incrementAndGet();
            return valor;
        });
    }

    private Supplier<Uni<String>> lecturaCaida() {
        return () -> Uni.createFrom().failure(SdkClientException.create("DynamoDB caido"));
    }

    private String leer(String clave, Supplier<Uni<String>> lectura) {
        return cache.obtener(clave, lectura).await().atMost(Duration.ofSeconds(5));
    }

    @Test
    void should_serveFromMemoryWithoutHittingDb_whenSnapshotIsFresh() {
        assertEquals("A", leer("k", lecturaOk("A")));
        ahora.addAndGet(30_000);
        assertEquals("A", leer("k", lecturaOk("B")));

        assertEquals(1, llamadas.get(), "dentro del ttl no debe volver a la base");
    }

    @Test
    void should_reloadFromDb_whenTtlExpired() {
        leer("k", lecturaOk("A"));
        ahora.addAndGet(61_000);

        assertEquals("B", leer("k", lecturaOk("B")));
    }

    @Test
    void should_serveStaleSnapshot_whenDbDownWithinTwoMinutesBeyondTtl() {
        leer("k", lecturaOk("A"));
        ahora.addAndGet(60_000 + 119_000);

        assertEquals("A", leer("k", lecturaCaida()));
    }

    @Test
    void should_fail503_whenDbDownBeyondTtlPlusRespaldo() {
        leer("k", lecturaOk("A"));
        ahora.addAndGet(60_000 + 121_000);

        UniAssertSubscriber<String> sub = cache.obtener("k", lecturaCaida())
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        sub.awaitFailure();
        assertInstanceOf(InfraestructuraNoDisponibleException.class, sub.getFailure());
    }

    @Test
    void should_fail503_whenDbDownAndNothingCachedYet() {
        UniAssertSubscriber<String> sub = cache.obtener("nuevo", lecturaCaida())
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        sub.awaitFailure();
        assertInstanceOf(InfraestructuraNoDisponibleException.class, sub.getFailure());
    }

    @Test
    void should_reloadAfterInvalidar_butKeepStaleAsBackupIfDbIsDown() {
        leer("cat:todos", lecturaOk("A"));
        long generacionAntes = cache.generacion();

        cache.invalidar("cat:");

        assertNotEquals(generacionAntes, cache.generacion());
        assertEquals("B", leer("cat:todos", lecturaOk("B")), "tras invalidar debe recargar aunque no venza el ttl");

        cache.invalidar("cat:");
        assertEquals("B", leer("cat:todos", lecturaCaida()), "con la base caida sirve el ultimo snapshot");
    }

    @Test
    void should_notInvalidateOtherPrefixes() {
        leer("menu:x", lecturaOk("A"));
        cache.invalidar("cat:");

        assertEquals("A", leer("menu:x", lecturaOk("B")));
    }

    @Test
    void should_propagateDomainFailuresUntouchedAndNotCacheThem() {
        Supplier<Uni<String>> noExiste = () -> Uni.createFrom().failure(new CatalogoNoEncontradoException(TipoCatalogo.AREA, "X"));
        UniAssertSubscriber<String> sub = cache.obtener("k", noExiste)
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        sub.awaitFailure();
        assertInstanceOf(CatalogoNoEncontradoException.class, sub.getFailure());

        assertEquals("A", leer("k", lecturaOk("A")), "un fallo de dominio no debe quedar cacheado");
    }

    @Test
    void should_shareOneDbCall_whenConcurrentRequestsLoadSameKey() {
        AtomicInteger dbCalls = new AtomicInteger();
        Supplier<Uni<String>> lenta = () -> Uni.createFrom().item("A")
                .onItem().invoke(dbCalls::incrementAndGet)
                .onItem().delayIt().by(Duration.ofMillis(200));

        Uni<String> u1 = cache.obtener("k", lenta);
        Uni<String> u2 = cache.obtener("k", lenta);
        UniAssertSubscriber<String> s1 = u1.subscribe().withSubscriber(UniAssertSubscriber.create());
        UniAssertSubscriber<String> s2 = u2.subscribe().withSubscriber(UniAssertSubscriber.create());
        s1.awaitItem(Duration.ofSeconds(5));
        s2.awaitItem(Duration.ofSeconds(5));

        assertEquals(1, dbCalls.get(), "dos lecturas concurrentes de la misma clave deben compartir una sola carga");
    }

    @Test
    void should_serveStaleQuickly_whenDbIsHangingAndBackupExists() {
        leer("k", lecturaOk("A"));
        ahora.addAndGet(61_000);
        Supplier<Uni<String>> colgada = () -> Uni.createFrom().item("B").onItem().delayIt().by(Duration.ofSeconds(5));

        long inicio = System.nanoTime();
        String valor = leer("k", colgada);
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertEquals("A", valor);
        org.junit.jupiter.api.Assertions.assertTrue(ms < 2_000, "no debe esperar a la base colgada: " + ms + " ms");
    }
}
