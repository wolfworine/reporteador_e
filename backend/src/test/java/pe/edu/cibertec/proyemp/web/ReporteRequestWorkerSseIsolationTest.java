package pe.edu.cibertec.proyemp.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.proyemp.domain.EstadoReporte;
import pe.edu.cibertec.proyemp.domain.ReporteEjecucion;
import pe.edu.cibertec.proyemp.messaging.ReportRequestedEvent;
import pe.edu.cibertec.proyemp.messaging.ReporteEvento;
import pe.edu.cibertec.proyemp.messaging.ReporteEventoBroadcaster;
import pe.edu.cibertec.proyemp.messaging.ReporteRequestWorker;
import pe.edu.cibertec.proyemp.report.ReportGeneratorPort;
import pe.edu.cibertec.proyemp.repository.ReporteEjecucionRepository;
import pe.edu.cibertec.proyemp.service.ReporteService;

import java.time.Duration;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Resuelve la nota no bloqueante dejada por la review de la feature 11
 * (ver {@code progress/review.md} de esa feature y {@code handoff.md}):
 * "considerar envolver {@code eventoBroadcaster.emitir(...)} dentro de
 * {@code publicarReady}/{@code publicarFailed} en
 * {@code messaging/ReporteRequestWorker.java} con manejo de error explícito
 * para que una falla del puente SSE nunca contamine el resultado del
 * worker Kafka".
 *
 * <p>Construye un {@link ReporteRequestWorker} manualmente (fuera de CDI,
 * mismos beans reales inyectados vía {@code @QuarkusTest} salvo el
 * {@link ReporteEventoBroadcaster}, sustituido por una subclase que siempre
 * lanza) para probar que un fallo del puente SSE NO hace que el reporte
 * termine {@code ERROR} ni que el {@code Uni} de {@code onReportRequested}
 * falle -- el resultado real (Kafka {@code report.ready} + DynamoDB
 * {@code estado=LISTO}) ya estaba confirmado antes de intentar emitir el
 * evento SSE.
 */
@QuarkusTest
class ReporteRequestWorkerSseIsolationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    ReporteService reporteService;

    @Inject
    ReporteEjecucionRepository repository;

    @Inject
    @Named("pdf")
    ReportGeneratorPort pdfAdapter;

    @Inject
    @Named("excel")
    ReportGeneratorPort excelAdapter;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Channel("report-ready-out")
    Emitter<String> reportReadyEmitter;

    @Inject
    @Channel("report-failed-out")
    Emitter<String> reportFailedEmitter;

    @Test
    void should_stillMarkListoAndNotFail_when_sseBroadcasterThrowsOnEmitir() {
        String aplicacion = RolResourceTest.crearAplicacion("APLICACION-SSE-ISOLATION");
        String ubicacion = RolResourceTest.crearUbicacion("UBIC-SSE-ISOLATION");
        String area = RolResourceTest.crearArea("AREA-SSE-ISOLATION");
        String cargo = RolResourceTest.crearCargo("CARGO-SSE-ISOLATION");
        String rol = given().contentType("application/json")
                .body(RolResourceTest.rolJson(ubicacion, area, cargo, "ROL-SSE-ISOLATION"))
                .when().post("/api/roles").then().statusCode(201).extract().path("id").toString();
        String suministro = given().contentType("application/json")
                .body("{\"nombre\":\"SUM-SSE-ISOLATION\",\"prioridad\":\"ALTA\"}")
                .when().post("/api/roles/" + rol + "/suministros").then().statusCode(201)
                .extract().path("id").toString();
        given().contentType("application/json")
                .body("{"
                        + "\"aplicacionCodigo\":\"" + aplicacion + "\","
                        + "\"rolId\":\"" + rol + "\","
                        + "\"suministroId\":\"" + suministro + "\","
                        + "\"categoria\":\"ROLES\","
                        + "\"atributoCodigo\":\"COD-SSE-ISOLATION\","
                        + "\"atributoNombre\":\"Atributo SSE isolation\","
                        + "\"estado\":\"ACTIVO\"}")
                .when().post("/api/atributos")
                .then().statusCode(201);

        ReporteEventoBroadcaster broadcasterQueFalla = new ReporteEventoBroadcaster() {
            @Override
            public void emitir(ReporteEvento evento) {
                throw new IllegalStateException("fallo simulado del puente SSE -- no deberia contaminar el worker");
            }
        };
        ReporteRequestWorker worker = new ReporteRequestWorker(reporteService, repository, pdfAdapter, excelAdapter,
                objectMapper, reportReadyEmitter, reportFailedEmitter, broadcasterQueFalla);

        String id = UUID.randomUUID().toString();
        repository.crearPendiente(id, aplicacion, area, "pdf").await().atMost(TIMEOUT);

        String payload = serializar(new ReportRequestedEvent(id, aplicacion, area, "pdf"));

        // Si emitirEventoSeguro no aislara el fallo del broadcaster, esta
        // llamada fallaria (el .invoke() propagaria la IllegalStateException
        // hacia arriba) y el reporte terminaria reintentado/ERROR en vez de
        // LISTO -- exactamente el bug que describe la nota de la review.
        worker.onReportRequested(payload).await().atMost(TIMEOUT);

        ReporteEjecucion resultado = repository.buscarPorId(id).await().atMost(TIMEOUT);
        assertEquals(EstadoReporte.LISTO, resultado.estado(),
                "un fallo del puente SSE no deberia impedir que el reporte quede LISTO");
        assertEquals(1, resultado.intentos(),
                "un fallo del puente SSE no deberia disparar un reintento (el resultado real ya estaba confirmado)");
        assertNotNull(resultado.binario());
    }

    private String serializar(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
