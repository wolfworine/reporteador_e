package pe.edu.cibertec.proyemp.web.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.kafka.common.KafkaException;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import pe.edu.cibertec.proyemp.domain.InfraestructuraNoDisponibleException;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * Mapea fallos de infraestructura externa (DynamoDB/Kafka no disponibles) a
 * HTTP 503 con un mensaje claro -- feature 12
 * ({@code reactive_resilience_observability}), acceptance 3.
 *
 * <p>Dos casos cubiertos:
 * <ul>
 *   <li>{@link InfraestructuraNoDisponibleException}: producida
 *   explícitamente por {@code web/UniFallosInfraestructura} en los
 *   endpoints de reportes (ver su Javadoc para el porqué de esa capa
 *   intermedia).</li>
 *   <li>{@link SdkException} (y sus subtipos, ej. {@code SdkClientException}
 *   cuando DynamoDB/Secrets Manager no responden) y {@link KafkaException}
 *   sin envolver -- cubre de forma centralizada TODOS los demás endpoints
 *   de escritura (catálogos simples, rol/suministro/atributo, usuario,
 *   menú) sin repetir el wrapper de {@code UniFallosInfraestructura} en
 *   cada uno de esos ~10 recursos REST: cualquier {@code Uni} que falle con
 *   una de estas excepciones (el {@code DynamoDbAsyncClient}/
 *   {@code SecretsManagerAsyncClient}/productor Kafka las lanza tal cual,
 *   sin que el código de este proyecto necesite interceptarlas una por una)
 *   termina aquí igual, con la misma respuesta 503 clara -- nunca en el 500
 *   genérico por defecto de RESTEasy Reactive que no distingue "reintente"
 *   de "hay un bug".</li>
 * </ul>
 *
 * <p>Deliberadamente NO se mapea aquí ningún otro {@code Throwable}/
 * {@code Exception} genérico -- eso seguiría cayendo en el 500 por defecto
 * de Quarkus, que es lo correcto para un bug real de programación (no
 * disfrazarlo de "servicio no disponible, reintente").
 */
public class InfraestructuraExceptionMappers {

    private static final Logger LOG = Logger.getLogger(InfraestructuraExceptionMappers.class);

    @ServerExceptionMapper
    public Response mapInfraestructuraNoDisponible(InfraestructuraNoDisponibleException exception) {
        LOG.warnf(exception.getCause(), "Fallo de infraestructura manejado explicitamente: %s", exception.getMessage());
        return respuesta(exception.getMessage());
    }

    @ServerExceptionMapper
    public Response mapSdkException(SdkException exception) {
        LOG.warnf(exception, "DynamoDB/Secrets Manager no disponible atendiendo una request");
        return respuesta("El almacenamiento de datos (DynamoDB/Secrets Manager) no esta disponible temporalmente. "
                + "Intente nuevamente en unos segundos.");
    }

    @ServerExceptionMapper
    public Response mapKafkaException(KafkaException exception) {
        LOG.warnf(exception, "Kafka no disponible atendiendo una request");
        return respuesta("El servicio de mensajeria (Kafka) no esta disponible temporalmente. "
                + "Intente nuevamente en unos segundos.");
    }

    private Response respuesta(String mensaje) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(mensaje))
                .build();
    }

    public record ErrorResponse(String mensaje) {
    }
}
