package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.proyemp.domain.EstadoReporte;
import pe.edu.cibertec.proyemp.domain.ReporteEjecucion;
import pe.edu.cibertec.proyemp.messaging.ReporteEvento;
import pe.edu.cibertec.proyemp.messaging.ReporteEventoBroadcaster;
import pe.edu.cibertec.proyemp.repository.ReporteEjecucionRepository;

import java.time.Duration;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test de integración de la feature 10
 * ({@code reportes_notificacion_sse_y_descarga}) contra Kafka + LocalStack
 * reales de Quarkus Dev Services (acceptance 5), mismo patrón que
 * {@code ReporteGenerarResourceTest} (feature 9): helpers estáticos de
 * {@code RolResourceTest} para crear los datos base.
 *
 * <ol>
 *   <li>Acceptance 1 (SSE): un test se suscribe directo al {@code Multi}
 *   de {@link ReporteEventoBroadcaster} (sin pasar por HTTP -- más simple y
 *   determinista para verificar la emisión en sí) y confirma que un evento
 *   publicado con {@code emitir(...)} llega al suscriptor; otro test
 *   confirma el circuito completo real: {@code POST /generar} -> worker
 *   consume {@code report.requested} -> publica {@code report.ready} ->
 *   {@code ReporteRequestWorker} alimenta el broadcaster -> el evento
 *   aparece en un suscriptor conectado ANTES de disparar la generación
 *   (docs/kafka-reactive-guidance.md, el SSE nunca consume Kafka
 *   directamente).</li>
 *   <li>Acceptance 2: {@code GET /{id}/descargar} 404 con id inexistente,
 *   404 con id {@code PENDIENTE}, 200 + binario + header
 *   {@code Content-Disposition: attachment} con id {@code LISTO}.</li>
 *   <li>Acceptance 3: {@code GET /{id}} 404 con id inexistente, 404 con id
 *   {@code PENDIENTE}, 200 + JSON {@code List<Reporte>} con id
 *   {@code LISTO}.</li>
 * </ol>
 */
@QuarkusTest
class ReporteResourceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_TOTAL = Duration.ofSeconds(30);

    @Inject
    ReporteEjecucionRepository repository;

    @Inject
    ReporteEventoBroadcaster eventoBroadcaster;

    @Test
    void should_deliverEventToSubscriber_when_broadcasterEmitsAfterSubscription() {
        AssertSubscriber<ReporteEvento> subscriber = eventoBroadcaster.stream()
                .subscribe().withSubscriber(AssertSubscriber.create(1));

        ReporteEvento evento = ReporteEvento.listo("id-prueba-sse");
        eventoBroadcaster.emitir(evento);

        subscriber.awaitItems(1, TIMEOUT);
        subscriber.assertItems(evento);
        subscriber.cancel();
    }

    @Test
    void should_pushReadyEventThroughSseBridge_when_workerPublishesReportReady() {
        Escenario e = crearEscenario("APP-SSE");
        crearRolConAtributo(e, "ROL-SSE", "ROLES", "COD-SSE", "Atributo SSE");

        AssertSubscriber<ReporteEvento> subscriber = eventoBroadcaster.stream()
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));

        String id = given().contentType("application/json")
                .body(generarRequestJson(e.aplicacion, e.area, "pdf"))
                .when().post("/api/reportes/generar")
                .then().statusCode(202).extract().path("id").toString();

        boolean encontrado = esperarEventoConId(subscriber, id, "LISTO");
        subscriber.cancel();

        assertTrue(encontrado, "no se recibio por SSE el evento LISTO para el id " + id);
    }

    @Test
    void should_return404_when_descargarConIdInexistente() {
        given().when().get("/api/reportes/" + UUID.randomUUID() + "/descargar")
                .then().statusCode(404);
    }

    @Test
    void should_return404_when_descargarConReportePendiente() {
        String id = UUID.randomUUID().toString();
        repository.crearPendiente(id, "APP-X", "AREA-X", "pdf").await().atMost(TIMEOUT);

        given().when().get("/api/reportes/" + id + "/descargar")
                .then().statusCode(404);
    }

    @Test
    void should_returnBinaryWithAttachmentHeader_when_descargarConReporteListo() {
        Escenario e = crearEscenario("APP-DESCARGA");
        crearRolConAtributo(e, "ROL-DESCARGA", "ROLES", "COD-DESCARGA", "Atributo Descarga");

        String id = given().contentType("application/json")
                .body(generarRequestJson(e.aplicacion, e.area, "pdf"))
                .when().post("/api/reportes/generar")
                .then().statusCode(202).extract().path("id").toString();

        esperarEstadoDistintoDePendiente(id);

        byte[] cuerpo = given().when().get("/api/reportes/" + id + "/descargar")
                .then().statusCode(200)
                .header("Content-Disposition", containsString("attachment"))
                .header("Content-Disposition", containsString("reporte-" + id))
                .contentType("application/pdf")
                .extract().asByteArray();

        assertTrue(cuerpo.length > 0, "el binario descargado no deberia estar vacio");
    }

    @Test
    void should_regenerateOnDemand_when_descargarPideElFormatoContrarioAlYaGenerado() {
        // Bug real reportado por el usuario 2026-08-25: generar en un
        // formato y pedir el OTRO por `?formato=` exigía generar de nuevo
        // desde cero (el query param se ignoraba por completo). Ahora se
        // regenera al vuelo reusando el arbol ya persistido, sin volver a
        // pasar por Kafka/el worker.
        Escenario e = crearEscenario("APP-REGEN");
        crearRolConAtributo(e, "ROL-REGEN", "ROLES", "COD-REGEN", "Atributo Regen");

        String id = given().contentType("application/json")
                .body(generarRequestJson(e.aplicacion, e.area, "pdf"))
                .when().post("/api/reportes/generar")
                .then().statusCode(202).extract().path("id").toString();

        esperarEstadoDistintoDePendiente(id);

        // El formato ya generado (pdf, sin query param) sigue sirviendo el binario cacheado.
        given().when().get("/api/reportes/" + id + "/descargar")
                .then().statusCode(200)
                .contentType("application/pdf");

        // Pedir excel (el que NO se generó originalmente) regenera al vuelo, no 404/500.
        byte[] excel = given().when().get("/api/reportes/" + id + "/descargar?formato=excel")
                .then().statusCode(200)
                .header("Content-Disposition", containsString("reporte-" + id + ".xlsx"))
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .extract().asByteArray();

        assertTrue(excel.length > 0, "el excel regenerado al vuelo no deberia estar vacio");
    }

    @Test
    void should_return400_when_descargarConFormatoInvalido() {
        Escenario e = crearEscenario("APP-FMT-INVALIDO");
        crearRolConAtributo(e, "ROL-FMT-INVALIDO", "ROLES", "COD-FMT-INVALIDO", "Atributo");

        String id = given().contentType("application/json")
                .body(generarRequestJson(e.aplicacion, e.area, "pdf"))
                .when().post("/api/reportes/generar")
                .then().statusCode(202).extract().path("id").toString();

        esperarEstadoDistintoDePendiente(id);

        given().when().get("/api/reportes/" + id + "/descargar?formato=word")
                .then().statusCode(400);
    }

    @Test
    void should_return404_when_obtenerConIdInexistente() {
        given().when().get("/api/reportes/" + UUID.randomUUID())
                .then().statusCode(404);
    }

    @Test
    void should_return404_when_obtenerConReportePendiente() {
        String id = UUID.randomUUID().toString();
        repository.crearPendiente(id, "APP-X", "AREA-X", "pdf").await().atMost(TIMEOUT);

        given().when().get("/api/reportes/" + id)
                .then().statusCode(404);
    }

    @Test
    void should_returnReporteTreeJson_when_obtenerConReporteListo() {
        Escenario e = crearEscenario("APP-ARBOL");
        crearRolConAtributo(e, "ROL-ARBOL", "ROLES", "COD-ARBOL", "Atributo Arbol");

        String id = given().contentType("application/json")
                .body(generarRequestJson(e.aplicacion, e.area, "pdf"))
                .when().post("/api/reportes/generar")
                .then().statusCode(202).extract().path("id").toString();

        esperarEstadoDistintoDePendiente(id);

        // Nombres de campo de web/dto/ReporteRespuestaDto (coordinación
        // cross-track 2026-08-19, no el dominio Reporte/DetalleRol crudo --
        // ver el Javadoc de ReporteResource#obtener y
        // ../frontend/src/types/reporte.ts).
        given().when().get("/api/reportes/" + id)
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].codigoArea", equalTo(e.area))
                .body("[0].codigoCargo", equalTo(e.cargo))
                .body("[0].detalleRol", hasSize(1))
                .body("[0].detalleRol[0].idRol", notNullValue())
                .body("[0].detalleRol[0].tipoRol", hasItem("ROLES"))
                .body("[0].detalleRol[0].suministroRoles[0].atributoCodigo", equalTo("COD-ARBOL"))
                .body("[0].detalleRol[0].suministroRoles[0].atributoNombre", equalTo("Atributo Arbol"))
                .body("[0].detalleRol[0].cantidadSuministros",
                        equalTo(1)); // alias de cantidadSuministroRol, ver Javadoc de DetalleRolRespuestaDto
    }

    private ReporteEjecucion esperarEstadoDistintoDePendiente(String id) {
        long limite = System.currentTimeMillis() + POLL_TOTAL.toMillis();
        ReporteEjecucion actual;
        do {
            actual = repository.buscarPorId(id).await().atMost(TIMEOUT);
            if (actual.estado() != EstadoReporte.PENDIENTE) {
                return actual;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                fail("interrumpido esperando que el worker procese el reporte " + id);
            }
        } while (System.currentTimeMillis() < limite);
        fail("el reporte " + id + " sigue PENDIENTE tras " + POLL_TOTAL.toSeconds() + "s -- estado: " + actual);
        throw new IllegalStateException("inalcanzable");
    }

    private boolean esperarEventoConId(AssertSubscriber<ReporteEvento> subscriber, String id, String estadoEsperado) {
        long limite = System.currentTimeMillis() + POLL_TOTAL.toMillis();
        while (System.currentTimeMillis() < limite) {
            boolean encontrado = subscriber.getItems().stream()
                    .anyMatch(evento -> evento.id().equals(id) && estadoEsperado.equals(evento.estado()));
            if (encontrado) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                fail("interrumpido esperando el evento SSE para el id " + id);
            }
        }
        return false;
    }

    private static String generarRequestJson(String idAplicacion, String idArea, String formato) {
        return "{\"idAplicacion\":\"" + idAplicacion + "\",\"idArea\":\"" + idArea + "\",\"formato\":\"" + formato
                + "\"}";
    }

    private record Escenario(String aplicacion, String ubicacion, String area, String cargo) {
    }

    private Escenario crearEscenario(String prefijo) {
        String aplicacion = RolResourceTest.crearAplicacion("APLICACION-" + prefijo);
        String ubicacion = RolResourceTest.crearUbicacion("UBIC-" + prefijo);
        String area = RolResourceTest.crearArea("AREA-" + prefijo);
        String cargo = RolResourceTest.crearCargo("CARGO-" + prefijo);
        return new Escenario(aplicacion, ubicacion, area, cargo);
    }

    private static void crearRolConAtributo(Escenario e, String nombreRol, String categoria, String atributoCodigo,
            String atributoNombre) {
        String rol = given().contentType("application/json")
                .body(RolResourceTest.rolJson(e.ubicacion, e.area, e.cargo, nombreRol))
                .when().post("/api/roles").then().statusCode(201).extract().path("id").toString();
        String suministro = given().contentType("application/json")
                .body("{\"nombre\":\"SUM-" + nombreRol + "\",\"prioridad\":\"ALTA\"}")
                .when().post("/api/roles/" + rol + "/suministros").then().statusCode(201)
                .extract().path("id").toString();
        given().contentType("application/json")
                .body("{"
                        + "\"aplicacionCodigo\":\"" + e.aplicacion + "\","
                        + "\"rolId\":\"" + rol + "\","
                        + "\"suministroId\":\"" + suministro + "\","
                        + "\"categoria\":\"" + categoria + "\","
                        + "\"atributoCodigo\":\"" + atributoCodigo + "\","
                        + "\"atributoNombre\":\"" + atributoNombre + "\","
                        + "\"estado\":\"ACTIVO\"}")
                .when().post("/api/atributos")
                .then().statusCode(201);
    }
}
