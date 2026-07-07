package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.DynamoDbClientProvider;
import com.roombooking.model.Room;
import com.roombooking.model.RoomError;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import module java.base;

/** AppSync direct-Lambda resolver for {@code Mutation.createRoom}. */
public class CreateRoomHandler implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public CreateRoomHandler() {
        this(DynamoDbClientProvider.client(), System.getenv().getOrDefault("ROOMS_TABLE_NAME", "Rooms"));
    }

    CreateRoomHandler(final DynamoDbClient dynamoDbClient, final String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public Object handleRequest(final Map<String, Object> event, final Context context) {
        Identity.requireAuthenticated(event);

        final Map<String, Object> arguments = castToMap(event.get("arguments"));
        final Map<String, Object> roomInput = castToMap(arguments.get("room"));

        final String name = (String) roomInput.get("name");
        final int capacity = ((Number) roomInput.get("capacity")).intValue();

        final List<String> errors = new ArrayList<>();
        if (name == null || name.isBlank()) {
            errors.add(RoomError.NameRequired.name());
        }
        if (capacity < 2) {
            errors.add(RoomError.CapacityTooLow.name());
        }

        final Map<String, Object> result = new HashMap<>();
        if (!errors.isEmpty()) {
            result.put("room", null);
            result.put("errors", errors);
            return result;
        }

        final Room room = new Room(UUID.randomUUID().toString(), name, capacity);
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(room.toItem())
                .build());

        result.put("room", room.toResponseMap());
        result.put("errors", errors);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToMap(final Object value) {
        return (Map<String, Object>) value;
    }
}
