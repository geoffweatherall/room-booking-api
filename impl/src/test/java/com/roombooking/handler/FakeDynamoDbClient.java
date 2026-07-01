package com.roombooking.handler;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
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
    public PutItemResponse putItem(PutItemRequest request) {
        tables.computeIfAbsent(request.tableName(), k -> new ArrayList<>()).add(request.item());
        return PutItemResponse.builder().build();
    }

    @Override
    public ScanResponse scan(ScanRequest request) {
        List<Map<String, AttributeValue>> items = tables.getOrDefault(request.tableName(), new ArrayList<>());
        return ScanResponse.builder().items(items).count(items.size()).build();
    }
}
