package pe.edu.cibertec.proyemp.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.proyemp.domain.EstadoReporte;
import pe.edu.cibertec.proyemp.domain.Reporte;
import pe.edu.cibertec.proyemp.domain.ReporteEjecucion;
import pe.edu.cibertec.proyemp.messaging.ReportRequestedEvent;
import pe.edu.cibertec.proyemp.messaging.ReporteRequestWorker;
import pe.edu.cibertec.proyemp.repository.ReporteEjecucionRepository;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test de integración de la feature 9 ({@code reportes_generacion_async_kafka})
 * contra Kafka + LocalStack reales de Quarkus Dev Services (acceptance 5).
 *
 * <p>Cubre, con datos reales creados vía los endpoints REST ya existentes
 * (mismo patrón que {@code ReporteServiceTest}, helpers estáticos de
 * {@link RolResourceTest}):
 * <ol>
 *   <li>{@code POST /api/reportes/generar} responde {@code 202 Accepted}
 *   con {@code id} inmediatamente, nunca el binario (acceptance 1).</li>
 *   <li>El worker ({@code @Incoming}) consume {@code report.requested},
 *   genera el resultado reutilizando la feature 7/8, lo persiste
 *   (binario + árbol JSON + {@code estado=LISTO}) y publica
 *   {@code report.ready} -- verificado leyendo el tópico Kafka real con un
 *   {@link KafkaConsumer} plano apuntado al {@code kafka.bootstrap.servers}
 *   que inyecta Dev Services (acceptance 3 y 5).</li>
 *   <li>Dedupe: una segunda entrega de {@code report.requested} para un
 *   reporte ya {@code LISTO} no regenera (el contador {@code intentos}
 *   persistido no avanza).</li>
 *   <li>Poison pill: un reporte cuya generación falla siempre (área
 *   inexistente) agota los {@code MAX_INTENTOS} reintentos, queda
 *   {@code ERROR} con {@code motivoError}, y publica {@code report.failed}.</li>
 * </ol>
 */
@QuarkusTest
class ReporteGenerarResourceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_TOTAL = Duration.ofSeconds(30);

    @Inject
    ReporteEjecucionRepository repository;

    @Inject
    ReporteRequestWorker worker;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String kafkaBootstrapServers;

    @Test
    void should_return202AcceptedWithIdOnly_when_postingGenerarConFormatoValido() {
        Escenario e = crearEscenario("APP-202");
        crearRolConAtributo(e, "ROL-202", "ROLES", "COD-202", "Atributo 202");

        given().contentType("application/json")
                .body(generarRequestJson(e.aplicacion, e.area, "pdf"))
                .when().post("/api/reportes/generar")
                .then()
                .statusCode(202)
                .body("id", notNullValue())
                .body("id", not(emptyOrNullString()))
                .body("$", not(org.hamcrest.Matchers.hasKey("binario")))
                .body("$", not(org.hamcrest.Matchers.hasKey("reporteJson")));
    }

    @Test
    void should_publishReportReadyAndPersistListoStateWithBinarioAndReporteJson_when_workerConsumesReportRequested()
            throws Exception {
        Escenario e = crearEscenario("APP-ROUNDTRIP");
        crearRolConAtributo(e, "ROL-ROUNDTRIP", "ROLES", "COD-RT", "Atributo Roundtrip");

        try (KafkaConsumer<String, String> reportReadyConsumer = crearConsumidor("report.ready")) {
            String id = given().contentType("application/json")
                    .body(generarRequestJson(e.aplicacion, e.area, "pdf"))
                    .when().post("/api/reportes/generar")
                    .then().statusCode(202).extract().path("id").toString();

            ReporteEjecucion listo = esperarEstadoDistintoDePendiente(id);

            assertEquals(EstadoReporte.LISTO, listo.estado());
            assertEquals("pdf", listo.formato());
            assertEquals(1, listo.intentos());
            assertNotNull(listo.binario());
            assertTrue(listo.binario().length > 0, "el binario del PDF no deberia estar vacio");
            assertNotNull(listo.reporteJson());

            List<Reporte> arbol = objectMapper.readValue(listo.reporteJson(), new TypeReference<List<Reporte>>() {
            });
            assertEquals(1, arbol.size());
            assertEquals(e.area, arbol.get(0).areaCodigo());
            assertEquals(1, arbol.get(0).detalleRol().size());

            String mensaje = esperarMensajeConId(reportReadyConsumer, id);
            assertTrue(mensaje.contains(id), "el mensaje de report.ready deberia contener el reporteId: " + mensaje);
        }
    }

    @Test
    void should_notRegenerate_when_reportAlreadyListo() throws Exception {
        Escenario e = crearEscenario("APP-DEDUPE");
        crearRolConAtributo(e, "ROL-DEDUPE", "ROLES", "COD-DEDUPE", "Atributo Dedupe");

        String id = given().contentType("application/json")
                .body(generarRequestJson(e.aplicacion, e.area, "excel"))
                .when().post("/api/reportes/generar")
                .then().statusCode(202).extract().path("id").toString();

        ReporteEjecucion listo = esperarEstadoDistintoDePendiente(id);
        assertEquals(EstadoReporte.LISTO, listo.estado());
        assertEquals(1, listo.intentos());

        // Redelivery simulada del mismo report.requested -- el worker debe
        // detectar estado=LISTO y NO regenerar (docs/kafka-reactive-guidance.md).
        String eventoJson = objectMapper
                .writeValueAsString(new ReportRequestedEvent(id, e.aplicacion, e.area, "excel"));
        worker.onReportRequested(eventoJson).await().atMost(TIMEOUT);

        ReporteEjecucion despuesDeRedelivery = repository.buscarPorId(id).await().atMost(TIMEOUT);
        assertEquals(EstadoReporte.LISTO, despuesDeRedelivery.estado());
        assertEquals(1, despuesDeRedelivery.intentos(),
                "el contador de intentos no deberia avanzar ante una redelivery de un reporte ya LISTO");
    }

    @Test
    void should_markErrorAndPublishReportFailed_afterExhaustingRetries_when_reportGenerationFailsRepeatedly()
            throws Exception {
        String aplicacion = RolResourceTest.crearAplicacion("APLICACION-APP-POISON");
        String areaInexistente = "AREA-NO-EXISTE-" + UUID.randomUUID();

        try (KafkaConsumer<String, String> reportFailedConsumer = crearConsumidor("report.failed")) {
            String id = given().contentType("application/json")
                    .body(generarRequestJson(aplicacion, areaInexistente, "pdf"))
                    .when().post("/api/reportes/generar")
                    .then().statusCode(202).extract().path("id").toString();

            ReporteEjecucion error = esperarEstadoDistintoDePendiente(id);

            assertEquals(EstadoReporte.ERROR, error.estado());
            assertEquals(3, error.intentos(), "el worker deberia agotar exactamente MAX_INTENTOS=3 reintentos");
            assertNotNull(error.motivoError());
            assertFalse(error.motivoError().isBlank());

            String mensaje = esperarMensajeConId(reportFailedConsumer, id);
            assertTrue(mensaje.contains(id), "el mensaje de report.failed deberia contener el reporteId: " + mensaje);
        }
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

    private String esperarMensajeConId(KafkaConsumer<String, String> consumer, String id) {
        long limite = System.currentTimeMillis() + POLL_TOTAL.toMillis();
        while (System.currentTimeMillis() < limite) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(id)) {
                    return record.value();
                }
            }
        }
        fail("no se recibio ningun mensaje conteniendo el id " + id + " en el topico suscrito tras "
                + POLL_TOTAL.toSeconds() + "s");
        throw new IllegalStateException("inalcanzable");
    }

    private KafkaConsumer<String, String> crearConsumidor(String topico) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + topico + "-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topico));
        return consumer;
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
