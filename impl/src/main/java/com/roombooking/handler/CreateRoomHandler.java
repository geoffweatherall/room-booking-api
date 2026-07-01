package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.DynamoDbClientProvider;
import com.roombooking.model.Room;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;
import java.util.UUID;

/** AppSync direct-Lambda resolver for {@code Mutation.createRoom}. */
public class CreateRoomHandler implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public CreateRoomHandler() {
        this(DynamoDbClientProvider.client(), System.getenv().getOrDefault("ROOMS_TABLE_NAME", "Rooms"));
    }

    CreateRoomHandler(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> arguments = castToMap(event.get("arguments"));
        Map<String, Object> roomInput = castToMap(arguments.get("room"));

        String name = (String) roomInput.get("name");
        int capacity = ((Number) roomInput.get("capacity")).intValue();
        Room room = new Room(UUID.randomUUID().toString(), name, capacity);

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(room.toItem())
                .build());

        return room.toResponseMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToMap(Object value) {
        return (Map<String, Object>) value;
    }
}
