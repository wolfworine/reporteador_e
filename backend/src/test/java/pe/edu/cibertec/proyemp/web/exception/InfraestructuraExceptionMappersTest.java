package pe.edu.cibertec.proyemp.web.exception;

import jakarta.ws.rs.core.Response;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.proyemp.domain.InfraestructuraNoDisponibleException;
import software.amazon.awssdk.core.exception.SdkException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature 12 ({@code reactive_resilience_observability}), acceptance 3:
 * "Los endpoints de reportes y de escritura (POST/PUT) tienen manejo de
 * error explícito que devuelve un código HTTP y mensaje claros ante fallo
 * de DynamoDB/Kafka".
 *
 * <p>Test unitario puro (sin {@code @QuarkusTest}, sin infraestructura) de
 * {@link InfraestructuraExceptionMappers}: prueba de forma determinista que
 * los tres tipos de fallo que puede propagar un {@code Uni} de DynamoDB/
 * Kafka producen exactamente 503 + un mensaje claro, sin depender de tumbar
 * LocalStack/Kafka reales a mitad de la suite (lo que además rompería el
 * arranque de {@code RoleRepositoryTableInitializer}/{@code AppSecretsBootstrap},
 * ver Javadoc de {@code infra/health/DynamoDbReadinessCheckTest}).
 */
class InfraestructuraExceptionMappersTest {

    private final InfraestructuraExceptionMappers mappers = new InfraestructuraExceptionMappers();

    @Test
    void should_return503WithClearMessage_when_infraestructuraNoDisponibleException() {
        SdkException causa = SdkException.create("connection refused", null);
        InfraestructuraNoDisponibleException excepcion = new InfraestructuraNoDisponibleException(
                "POST /api/reportes/generar id=abc", causa);

        Response respuesta = mappers.mapInfraestructuraNoDisponible(excepcion);

        assertEquals(503, respuesta.getStatus());
        InfraestructuraExceptionMappers.ErrorResponse entidad = assertInstanceOf(
                InfraestructuraExceptionMappers.ErrorResponse.class, respuesta.getEntity());
        assertTrue(entidad.mensaje().contains("POST /api/reportes/generar id=abc"),
                "el mensaje deberia incluir el contexto de la operacion que fallo");
        assertTrue(entidad.mensaje().contains("connection refused"),
                "el mensaje deberia incluir la causa real del fallo de infraestructura");
    }

    @Test
    void should_return503WithClearMessage_when_sdkExceptionFromDynamoDbOrSecretsManager() {
        SdkException excepcion = SdkException.create("Unable to execute HTTP request", null);

        Response respuesta = mappers.mapSdkException(excepcion);

        assertEquals(503, respuesta.getStatus());
        InfraestructuraExceptionMappers.ErrorResponse entidad = assertInstanceOf(
                InfraestructuraExceptionMappers.ErrorResponse.class, respuesta.getEntity());
        assertFalse(entidad.mensaje().isBlank());
        assertTrue(entidad.mensaje().toLowerCase().contains("dynamodb")
                        || entidad.mensaje().toLowerCase().contains("secrets manager"),
                "el mensaje deberia identificar claramente que el fallo es de almacenamiento, no un 500 generico");
    }

    @Test
    void should_return503WithClearMessage_when_kafkaException() {
        KafkaException excepcion = new KafkaException("broker not available");

        Response respuesta = mappers.mapKafkaException(excepcion);

        assertEquals(503, respuesta.getStatus());
        InfraestructuraExceptionMappers.ErrorResponse entidad = assertInstanceOf(
                InfraestructuraExceptionMappers.ErrorResponse.class, respuesta.getEntity());
        assertFalse(entidad.mensaje().isBlank());
        assertTrue(entidad.mensaje().toLowerCase().contains("kafka"),
                "el mensaje deberia identificar claramente que el fallo es de mensajeria, no un 500 generico");
    }
}
