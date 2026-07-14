package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.DynamoDbClientProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import module java.base;

/**
 * AppSync direct-Lambda resolver for {@code Mutation.reset}. Deletes all stored rooms and
 * bookings, and every person <b>except</b> those linked to a real Cognito account (identified by
 * a non-null {@code cognitoSub}) - so signed-up users never lose the Person record their account
 * is linked to just because someone reset a shared, non-production environment.
 */
public class ResetHandler implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient dynamoDbClient;
    private final String roomsTableName;
    private final String peopleTableName;
    private final String bookingsTableName;

    public ResetHandler() {
        this(DynamoDbClientProvider.client(),
                System.getenv().getOrDefault("ROOMS_TABLE_NAME", "Rooms"),
                System.getenv().getOrDefault("PEOPLE_TABLE_NAME", "People"),
                System.getenv().getOrDefault("BOOKINGS_TABLE_NAME", "Bookings"));
    }

    ResetHandler(final DynamoDbClient dynamoDbClient, final String roomsTableName, final String peopleTableName,
            final String bookingsTableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.roomsTableName = roomsTableName;
        this.peopleTableName = peopleTableName;
        this.bookingsTableName = bookingsTableName;
    }

    @Override
    public Object handleRequest(final Map<String, Object> event, final Context context) {
        Identity.requireAuthenticated(event);

        deleteAllItems(roomsTableName);
        deleteUnlinkedPeople();
        deleteAllItems(bookingsTableName);
        return true;
    }

    private void deleteAllItems(final String tableName) {
        final ScanResponse response = dynamoDbClient.scan(ScanRequest.builder().tableName(tableName).build());
        for (final Map<String, AttributeValue> item : response.items()) {
            dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("id", item.get("id")))
                    .build());
        }
    }

    private void deleteUnlinkedPeople() {
        final ScanResponse response = dynamoDbClient.scan(ScanRequest.builder().tableName(peopleTableName).build());
        for (final Map<String, AttributeValue> item : response.items()) {
            if (item.containsKey("cognitoSub")) {
                continue;
            }
            dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                    .tableName(peopleTableName)
                    .key(Map.of("id", item.get("id")))
                    .build());
        }
    }
}
