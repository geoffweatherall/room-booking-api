package com.roombooking.handler;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        tables.computeIfAbsent(request.tableName(), k -> new ArrayList<>()).add(request.item());
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
