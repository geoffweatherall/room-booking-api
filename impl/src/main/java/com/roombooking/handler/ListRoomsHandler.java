package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.DynamoDbClientProvider;
import com.roombooking.model.Room;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        final ScanResponse response = dynamoDbClient.scan(ScanRequest.builder().tableName(tableName).build());
        final List<Map<String, Object>> rooms = response.items().stream()
                .map(Room::fromItem)
                .map(Room::toResponseMap)
                .collect(Collectors.toList());
        return rooms;
    }
}
