package com.roombooking.handler;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import module java.base;

/** Minimal in-memory test double covering only the operations the handlers under test use. */
class FakeDynamoDbClient implements DynamoDbClient {

    final Map<String, List<Map<String, AttributeValue>>> tables = new HashMap<>();

    @Override
    public String serviceName() {
        return "dynamodb";
    }

    @Override
    public void close() {
    }

    @Override
    public PutItemResponse putItem(final PutItemRequest request) {
        tables.computeIfAbsent(request.tableName(), _ -> new ArrayList<>()).add(request.item());
        return PutItemResponse.builder().build();
    }

    @Override
    public ScanResponse scan(final ScanRequest request) {
        final List<Map<String, AttributeValue>> items = tables.getOrDefault(request.tableName(), new ArrayList<>());
        return ScanResponse.builder().items(items).count(items.size()).build();
    }

    @Override
    public GetItemResponse getItem(final GetItemRequest request) {
        final String id = request.key().get("id").s();
        final List<Map<String, AttributeValue>> items = tables.getOrDefault(request.tableName(), new ArrayList<>());
        return items.stream()
                .filter(item -> id.equals(item.get("id").s()))
                .findFirst()
                .map(item -> GetItemResponse.builder().item(item).build())
                .orElseGet(() -> GetItemResponse.builder().build());
    }

    /**
     * Supports only the single-equality-condition queries ({@code "attr = :value"}) the handlers
     * under test issue against a GSI; matches by scanning the table's items rather than modelling
     * indexes, since the fake only needs to behave the same as the real query, not perform like it.
     */
    @Override
    public QueryResponse query(final QueryRequest request) {
        final String[] parts = request.keyConditionExpression().split("=", 2);
        final String attributeName = parts[0].trim();
        final AttributeValue attributeValue = request.expressionAttributeValues().get(parts[1].trim());

        final List<Map<String, AttributeValue>> items = tables.getOrDefault(request.tableName(), new ArrayList<>()).stream()
                .filter(item -> attributeValue.equals(item.get(attributeName)))
                .toList();
        return QueryResponse.builder().items(items).count(items.size()).build();
    }

    /** Supports only single-table requests with no unprocessed keys, which is all the handlers under test issue. */
    @Override
    public BatchGetItemResponse batchGetItem(final BatchGetItemRequest request) {
        final Map<String, List<Map<String, AttributeValue>>> responses = new HashMap<>();
        for (final Map.Entry<String, KeysAndAttributes> entry : request.requestItems().entrySet()) {
            final String tableName = entry.getKey();
            final List<Map<String, AttributeValue>> tableItems = tables.getOrDefault(tableName, new ArrayList<>());
            final List<Map<String, AttributeValue>> matches = entry.getValue().keys().stream()
                    .map(key -> key.get("id").s())
                    .flatMap(id -> tableItems.stream().filter(item -> id.equals(item.get("id").s())))
                    .toList();
            responses.put(tableName, matches);
        }
        return BatchGetItemResponse.builder().responses(responses).unprocessedKeys(Map.of()).build();
    }

    @Override
    public DeleteItemResponse deleteItem(final DeleteItemRequest request) {
        final String id = request.key().get("id").s();
        final List<Map<String, AttributeValue>> items = tables.get(request.tableName());
        if (items != null) {
            items.removeIf(item -> id.equals(item.get("id").s()));
        }
        return DeleteItemResponse.builder().build();
    }
}
