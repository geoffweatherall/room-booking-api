package com.roombooking.dynamo;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

import module java.base;

/**
 * Looks up a set of ids in a single table keyed by {@code id}, deduplicating repeated ids and
 * batching requests via {@code BatchGetItem} (max 100 keys per request, so more than 100 distinct
 * ids are split into chunks fetched in parallel). Used to resolve the room/organiser/attendee ids
 * stored on a {@link com.roombooking.model.BookingRecord} back into full {@code Room}/{@code
 * Person} items without looking up the same id twice.
 */
public final class BatchLoader {

    private static final int BATCH_GET_ITEM_LIMIT = 100;

    private BatchLoader() {
    }

    public static Map<String, Map<String, AttributeValue>> loadById(
            final DynamoDbClient dynamoDbClient, final String tableName, final Set<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        final List<List<String>> chunks = chunk(List.copyOf(ids), BATCH_GET_ITEM_LIMIT);
        return chunks.stream()
                .map(chunkIds -> CompletableFuture.supplyAsync(() -> fetchChunk(dynamoDbClient, tableName, chunkIds)))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toMap(item -> item.get("id").s(), Function.identity()));
    }

    private static List<Map<String, AttributeValue>> fetchChunk(
            final DynamoDbClient dynamoDbClient, final String tableName, final List<String> ids) {
        Map<String, KeysAndAttributes> requestItems = Map.of(tableName, KeysAndAttributes.builder()
                .keys(ids.stream().map(id -> Map.of("id", AttributeValue.builder().s(id).build())).toList())
                .build());

        final List<Map<String, AttributeValue>> items = new ArrayList<>();
        while (requestItems != null && !requestItems.isEmpty()) {
            final BatchGetItemResponse response = dynamoDbClient.batchGetItem(
                    BatchGetItemRequest.builder().requestItems(requestItems).build());
            items.addAll(response.responses().getOrDefault(tableName, List.of()));
            requestItems = response.unprocessedKeys();
        }
        return items;
    }

    private static <T> List<List<T>> chunk(final List<T> items, final int size) {
        final List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            chunks.add(items.subList(i, Math.min(i + size, items.size())));
        }
        return chunks;
    }
}
