package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Uni;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pagina un {@code Query} completo (todas las paginas, no solo la primera).
 * Mismo bug/motivo que {@link DynamoScanSupport} (ver su Javadoc): {@code
 * Query} tiene el mismo limite de ~1MB por respuesta que {@code Scan}, y
 * {@code AtributoRepository} (GSI1, patrones 2/3/4) lo llamaba una sola vez.
 * Con particiones de GSI1 grandes (una combinacion Aplicacion+Area con
 * muchos Atributo -- el caso real tras migrar ~15.5k items reales) esto
 * truncaba resultados en silencio, igual que el Scan sin paginar.
 */
final class DynamoQuerySupport {

    private DynamoQuerySupport() {
    }

    static Uni<List<Map<String, AttributeValue>>> queryAll(
            DynamoDbAsyncClient client,
            java.util.function.Function<Map<String, AttributeValue>, QueryRequest> requestForKey) {
        return queryPage(client, requestForKey, null, new ArrayList<>());
    }

    private static Uni<List<Map<String, AttributeValue>>> queryPage(
            DynamoDbAsyncClient client,
            java.util.function.Function<Map<String, AttributeValue>, QueryRequest> requestForKey,
            Map<String, AttributeValue> exclusiveStartKey,
            List<Map<String, AttributeValue>> accumulated) {
        QueryRequest request = requestForKey.apply(exclusiveStartKey);
        return Uni.createFrom().completionStage(() -> client.query(request))
                .onItem().transformToUni(response -> {
                    accumulated.addAll(response.items());
                    Map<String, AttributeValue> nextKey = response.lastEvaluatedKey();
                    if (nextKey == null || nextKey.isEmpty()) {
                        return Uni.createFrom().item(accumulated);
                    }
                    return queryPage(client, requestForKey, nextKey, accumulated);
                });
    }
}
