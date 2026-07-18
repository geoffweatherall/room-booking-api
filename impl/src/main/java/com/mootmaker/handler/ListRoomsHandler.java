package com.mootmaker.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mootmaker.dynamo.DynamoDbClientProvider;
import com.mootmaker.model.Room;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import module java.base;

/** AppSync direct-Lambda resolver for {@code Query.rooms}. */
public class ListRoomsHandler implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public ListRoomsHandler() {
        this(DynamoDbClientProvider.client(), System.getenv().getOrDefault("ROOMS_TABLE_NAME", "Rooms"));
    }

    ListRoomsHandler(final DynamoDbClient dynamoDbClient, final String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public Object handleRequest(final Map<String, Object> event, final Context context) {
        Identity.requireAuthenticated(event);

        final ScanResponse response = dynamoDbClient.scan(ScanRequest.builder().tableName(tableName).build());
        return response.items().stream()
                .map(Room::fromItem)
                .map(Room::toResponseMap)
                .toList();
    }
}
