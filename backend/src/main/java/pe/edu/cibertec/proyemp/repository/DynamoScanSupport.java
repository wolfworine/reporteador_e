package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Uni;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pagina un {@code Scan} completo (todas las paginas, no solo la primera).
 *
 * <p>Bug real encontrado y corregido 2026-08-19: {@code CatalogoSimpleRepository},
 * {@code RolRepository} y {@code UsuarioRepository} llamaban a {@code
 * client.scan(request)} una sola vez y devolvian directo
 * {@code response.items()} -- DynamoDB (y LocalStack) limitan cada
 * respuesta de {@code Scan} a ~1MB de datos escaneados (antes del
 * {@code FilterExpression}), así que con volumen real (confirmado tras
 * migrar ~17.9k items reales: {@code /api/areas} devolvia 13 de 69,
 * {@code /api/roles} 113 de 741) esto devolvia paginas parciales en
 * silencio, sin error -- nunca se detecto porque los tests de este proyecto
 * (95/95 verdes) siempre trabajan con datasets pequeños de prueba (unas
 * pocas filas por test), nunca con datos migrados a escala real. Esta
 * utilidad sigue {@code lastEvaluatedKey} recursivamente hasta agotar todas
 * las paginas antes de devolver el resultado completo.
 */
final class DynamoScanSupport {

    private DynamoScanSupport() {
    }

    /**
     * @param client       cliente DynamoDB async.
     * @param requestForKey función que construye el {@link ScanRequest} de la
     *                      siguiente pagina a partir de la clave de
     *                      continuacion ({@code null} para la primera).
     */
    static Uni<List<Map<String, AttributeValue>>> scanAll(
            DynamoDbAsyncClient client,
            java.util.function.Function<Map<String, AttributeValue>, ScanRequest> requestForKey) {
        return scanPage(client, requestForKey, null, new ArrayList<>());
    }

    private static Uni<List<Map<String, AttributeValue>>> scanPage(
            DynamoDbAsyncClient client,
            java.util.function.Function<Map<String, AttributeValue>, ScanRequest> requestForKey,
            Map<String, AttributeValue> exclusiveStartKey,
            List<Map<String, AttributeValue>> accumulated) {
        ScanRequest request = requestForKey.apply(exclusiveStartKey);
        return Uni.createFrom().completionStage(() -> client.scan(request))
                .onItem().transformToUni(response -> {
                    accumulated.addAll(response.items());
                    Map<String, AttributeValue> nextKey = response.lastEvaluatedKey();
                    if (nextKey == null || nextKey.isEmpty()) {
                        return Uni.createFrom().item(accumulated);
                    }
                    return scanPage(client, requestForKey, nextKey, accumulated);
                });
    }
}
