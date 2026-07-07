package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.DynamoDbClientProvider;
import com.roombooking.model.Person;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import module java.base;

/** AppSync direct-Lambda resolver for {@code Query.people}. */
public class ListPeopleHandler implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public ListPeopleHandler() {
        this(DynamoDbClientProvider.client(), System.getenv().getOrDefault("PEOPLE_TABLE_NAME", "People"));
    }

    ListPeopleHandler(final DynamoDbClient dynamoDbClient, final String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public Object handleRequest(final Map<String, Object> event, final Context context) {
        Identity.requireAuthenticated(event);

        final ScanResponse response = dynamoDbClient.scan(ScanRequest.builder().tableName(tableName).build());
        return response.items().stream()
                .map(Person::fromItem)
                .map(Person::toResponseMap)
                .toList();
    }
}
