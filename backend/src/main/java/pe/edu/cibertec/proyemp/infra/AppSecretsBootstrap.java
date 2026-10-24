package pe.edu.cibertec.proyemp.infra;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import pe.edu.cibertec.proyemp.repository.RoleRepositoryTable;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceExistsException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resuelve al arranque de Quarkus (feature 11 de {@code feature_list.json},
 * {@code aws_secrets_manager_integration}) un dato de configuración de la
 * app -- el nombre de la tabla DynamoDB principal, {@link
 * RoleRepositoryTable#TABLE_NAME} -- leyéndolo desde AWS Secrets Manager
 * (emulado en LocalStack en {@code %dev}/{@code %test}) en vez de
 * asumirlo como una constante hardcodeada de confianza ciega en el código.
 *
 * <p><b>Por qué el nombre de tabla y no otro dato:</b> es el candidato más
 * defendible ya presente en el proyecto ("tabla/endpoint sensible" que pide
 * el acceptance de la feature 11) -- en un despliegue real no es deseable
 * que el nombre físico de la tabla que persiste toda la información de
 * roles/accesos de Entel viva como texto plano en el artefacto desplegable.
 * Es, eso sí, un ejemplo <b>demostrativo</b> del mecanismo de resolución de
 * secretos, no un secreto de negocio real (el nombre de una tabla DynamoDB
 * no es sensible por sí mismo) -- documentado también en
 * {@code docs/architecture.md}. Esta clase deliberadamente NO reemplaza
 * {@link RoleRepositoryTable#TABLE_NAME} en los ~10 repositorios existentes
 * que ya la usan como constante (fuera de scope de esta feature, evita
 * reabrir 8 features previas ya aprobadas); en vez de eso, el valor
 * resuelto se usa como el contenido inicial del propio secreto, para que la
 * demostración sea coherente ("el secreto que Secrets Manager entrega es,
 * en efecto, el nombre real de la tabla que usa la app").
 *
 * <p><b>Por qué un bean con {@code @Observes StartupEvent} y no el
 * mecanismo nativo {@code quarkus.secretsmanager.config.secrets} de la
 * extensión {@code quarkus-amazon-secretsmanager} (confirmado presente en
 * la versión 3.21.1 que resuelve el BOM de este proyecto, vía inspección
 * del jar -- clase {@code SecretsManagerConfigSourceFactory}):</b> ese
 * mecanismo nativo resuelve los valores ANTES de que arranque el
 * contenedor CDI (durante el bootstrap de configuración de Quarkus), lo
 * que exigiría que el secreto ya existiera en Secrets Manager antes del
 * primer arranque de la app -- awkward de garantizar en un
 * {@code @QuarkusTest} contra un LocalStack efímero de Dev Services, que
 * arranca en el mismo proceso que la propia resolución de config. El
 * patrón elegido aquí -- mismo que {@code
 * repository/RoleRepositoryTableInitializer} y {@code
 * repository/MenuTableInitializer} ya usan para DynamoDB, aprobado dos
 * veces por el reviewer -- autoprovisiona el secreto si no existe
 * (idempotente) y lo lee de vuelta con una llamada real a Secrets Manager,
 * 100% verificable dentro de un único arranque de {@code @QuarkusTest} sin
 * depender de que un arranque previo haya dejado el secreto sembrado.
 */
@ApplicationScoped
public class AppSecretsBootstrap {

    private static final Logger LOG = Logger.getLogger(AppSecretsBootstrap.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Nombre del secreto en Secrets Manager. Namespacing {@code app/...}
     * para no colisionar con el secreto de conectividad genérico de la
     * feature 2 ({@code test-connectivity-secret}, ver {@code
     * infra/SecretsManagerConnectivityTest} en {@code src/test}).
     */
    public static final String DYNAMO_TABLE_NAME_SECRET_ID = "app/dynamodb/table-name";

    private final SecretsManagerAsyncClient secretsManagerAsyncClient;
    private final AtomicReference<String> dynamoTableNameFromSecretsManager = new AtomicReference<>();

    @Inject
    public AppSecretsBootstrap(SecretsManagerAsyncClient secretsManagerAsyncClient) {
        this.secretsManagerAsyncClient = secretsManagerAsyncClient;
    }

    /**
     * {@code .await()} permitido aquí por la misma excepción explícita de
     * docs/conventions.md que ya usa {@code RoleRepositoryTableInitializer}
     * ("solo se permite en tests o en el main/bootstrap") -- corre una
     * única vez al arrancar, antes de servir cualquier request.
     */
    void onStart(@Observes StartupEvent event) {
        resolverDynamoTableNameDesdeSecretsManager().await().atMost(TIMEOUT);
    }

    /**
     * Lee {@value #DYNAMO_TABLE_NAME_SECRET_ID} de Secrets Manager; si no
     * existe todavía (primer arranque de un LocalStack vacío), lo crea con
     * el valor de {@link RoleRepositoryTable#TABLE_NAME} y vuelve a
     * leerlo -- el valor final expuesto por {@link
     * #dynamoTableNameFromSecretsManager()} SIEMPRE proviene de una
     * llamada {@code GetSecretValue} real, nunca de la constante en
     * memoria usada para poblar el alta.
     */
    Uni<String> resolverDynamoTableNameDesdeSecretsManager() {
        return leerSecreto()
                .onFailure(ResourceNotFoundException.class)
                .recoverWithUni(() -> crearSecretoSiNoExiste().chain(this::leerSecreto))
                .invoke(valor -> {
                    dynamoTableNameFromSecretsManager.set(valor);
                    LOG.infof("Secreto '%s' resuelto desde AWS Secrets Manager en el arranque", DYNAMO_TABLE_NAME_SECRET_ID);
                })
                .onFailure().invoke(failure -> LOG.warnf(failure,
                        "No se pudo resolver el secreto '%s' desde Secrets Manager en el arranque",
                        DYNAMO_TABLE_NAME_SECRET_ID));
    }

    private Uni<String> leerSecreto() {
        return Uni.createFrom()
                .completionStage(() -> secretsManagerAsyncClient.getSecretValue(GetSecretValueRequest.builder()
                        .secretId(DYNAMO_TABLE_NAME_SECRET_ID)
                        .build()))
                .onItem().transform(GetSecretValueResponse::secretString);
    }

    private Uni<Void> crearSecretoSiNoExiste() {
        return Uni.createFrom()
                .completionStage(() -> secretsManagerAsyncClient.createSecret(CreateSecretRequest.builder()
                        .name(DYNAMO_TABLE_NAME_SECRET_ID)
                        .secretString(RoleRepositoryTable.TABLE_NAME)
                        .build()))
                .onItem().transform(response -> (Void) null)
                // Ventana de carrera con otra instancia arrancando en paralelo (p.ej. Dev
                // Services compartido entre varias clases @QuarkusTest): si ya existe, no es
                // un error, simplemente seguimos a leerlo.
                .onFailure(ResourceExistsException.class).recoverWithItem(() -> null);
    }

    /**
     * Valor resuelto en el arranque, o {@code null} si el arranque todavía
     * no completó o falló (ver log de advertencia). Ejemplo demostrativo
     * de consumo -- documentado en la clase, no se rediseñan los
     * repositorios existentes para leer de aquí en vez de la constante
     * {@link RoleRepositoryTable#TABLE_NAME}.
     */
    public String dynamoTableNameFromSecretsManager() {
        return dynamoTableNameFromSecretsManager.get();
    }
}
