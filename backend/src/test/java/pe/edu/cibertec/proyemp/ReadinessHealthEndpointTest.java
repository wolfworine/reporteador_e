package pe.edu.cibertec.proyemp;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * Feature 12 ({@code reactive_resilience_observability}), acceptance 1:
 * "GET /q/health/ready reporta DOWN si LocalStack/Kafka no están
 * disponibles, UP si lo están".
 *
 * <p>Este test cubre el caso UP (LocalStack y Kafka realmente disponibles
 * vía Quarkus Dev Services, como en toda la suite): confirma que
 * {@code /q/health/ready} agrega los tres checks nuevos/existentes --
 * {@code infra/health/DynamoDbReadinessCheck},
 * {@code infra/health/SecretsManagerReadinessCheck} (ambos nuevos de esta
 * feature) y el check de SmallRye Reactive Messaging para los canales Kafka
 * (ya registrado automáticamente por {@code quarkus-messaging-kafka} desde
 * la feature 1, confirmado explorando {@code /q/health/ready} antes de
 * escribir esta feature -- no hacía falta escribir uno propio para Kafka).
 * El caso DOWN de cada dependencia individual se cubre con más control en
 * {@code infra/health/DynamoDbReadinessCheckTest}/
 * {@code SecretsManagerReadinessCheckTest} (clientes standalone apuntados a
 * un endpoint inalcanzable, sin arriesgar el arranque de toda la suite --
 * ver el Javadoc de esos tests para el porqué).
 */
@QuarkusTest
class ReadinessHealthEndpointTest {

    @Test
    void should_return200WithUpStatus_when_dynamoDbSecretsManagerAndKafkaAreAvailable() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks.name", hasItem("DynamoDB (LocalStack)"))
                .body("checks.name", hasItem("AWS Secrets Manager (LocalStack)"))
                .body("checks.find { it.name == 'DynamoDB (LocalStack)' }.status", equalTo("UP"))
                .body("checks.find { it.name == 'AWS Secrets Manager (LocalStack)' }.status", equalTo("UP"));
    }
}
