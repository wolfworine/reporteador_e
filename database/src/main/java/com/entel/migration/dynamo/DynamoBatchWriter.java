package com.entel.migration.dynamo;

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Escribe una lista de items en DynamoDB con {@code BatchWriteItem}, en
 * lotes de hasta 25 (limite real de la API), con reintento de
 * {@code UnprocessedItems} y concurrencia acotada para que cargar el volumen
 * real (~17k items en {@code RoleRepository}) no tome un tiempo excesivo.
 *
 * <p><b>Idempotente por diseno</b>: cada {@code PutRequest} dentro de un lote
 * sobreescribe el item con la misma PK/SK (deterministica, ver
 * {@code com.entel.migration.transform.RoleRepositoryTransformer} y
 * docs/architecture.md) - correr {@link #writeAll} dos veces con los mismos
 * items no duplica nada, solo vuelve a escribir el mismo valor.
 *
 * <p>Uso de {@code .join()}/bloqueo controlado sobre {@link CompletableFuture}
 * permitido por docs/conventions.md para esta herramienta CLI (no es un
 * servidor reactivo, es un batch job).
 */
public final class DynamoBatchWriter {

    private static final int BATCH_SIZE = 25;
    private static final int MAX_CONCURRENT_BATCHES = 8;
    private static final int MAX_RETRIES = 8;

    private DynamoBatchWriter() {
    }

    /**
     * Escribe todos los items en {@code tableName}. Devuelve la cantidad de
     * items enviados (no necesariamente todos "nuevos": PutItem sobreescribe
     * silenciosamente si la clave ya existia, que es exactamente el
     * comportamiento idempotente buscado).
     *
     * @throws IllegalStateException si algun lote sigue teniendo
     *                                {@code UnprocessedItems} tras agotar los
     *                                reintentos (fallo real de DynamoDB/LocalStack,
     *                                no un caso esperado en operacion normal).
     */
    public static int writeAll(DynamoDbAsyncClient client, String tableName, List<Map<String, AttributeValue>> items) {
        if (items.isEmpty()) {
            return 0;
        }
        List<List<Map<String, AttributeValue>>> chunks = partition(items, BATCH_SIZE);
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(MAX_CONCURRENT_BATCHES, chunks.size()));
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());
            for (List<Map<String, AttributeValue>> chunk : chunks) {
                futures.add(CompletableFuture.runAsync(() -> writeChunkWithRetry(client, tableName, chunk), executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return items.size();
        } finally {
            executor.shutdown();
            awaitTermination(executor);
        }
    }

    private static void writeChunkWithRetry(
            DynamoDbAsyncClient client, String tableName, List<Map<String, AttributeValue>> chunk) {
        List<WriteRequest> requests = new ArrayList<>(chunk.size());
        for (Map<String, AttributeValue> item : chunk) {
            requests.add(WriteRequest.builder().putRequest(PutRequest.builder().item(item).build()).build());
        }
        Map<String, List<WriteRequest>> requestItems = new HashMap<>();
        requestItems.put(tableName, requests);

        int attempt = 0;
        while (requestItems != null && !requestItems.isEmpty()) {
            attempt++;
            BatchWriteItemResponse response = client.batchWriteItem(
                            BatchWriteItemRequest.builder().requestItems(requestItems).build())
                    .join();
            requestItems = response.unprocessedItems();
            if (requestItems != null && !requestItems.isEmpty()) {
                if (attempt >= MAX_RETRIES) {
                    int pending = requestItems.values().stream().mapToInt(List::size).sum();
                    throw new IllegalStateException(
                            "No se pudieron escribir " + pending + " items en '" + tableName + "' tras "
                                    + MAX_RETRIES + " reintentos (UnprocessedItems persistente).");
                }
                sleepBackoff(attempt);
            }
        }
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Math.min(50L * attempt, 500L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido esperando reintento de BatchWriteItem", e);
        }
    }

    private static void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(2, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
