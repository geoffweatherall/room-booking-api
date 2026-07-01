package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.DynamoDbClientProvider;
import com.roombooking.model.Person;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;
import java.util.UUID;

/** AppSync direct-Lambda resolver for {@code Mutation.createPerson}. */
public class CreatePersonHandler implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public CreatePersonHandler() {
        this(DynamoDbClientProvider.client(), System.getenv().getOrDefault("PEOPLE_TABLE_NAME", "People"));
    }

    CreatePersonHandler(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> arguments = castToMap(event.get("arguments"));
        Map<String, Object> personInput = castToMap(arguments.get("person"));

        String name = (String) personInput.get("name");
        Person person = new Person(UUID.randomUUID().toString(), name);

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(person.toItem())
                .build());

        return person.toResponseMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToMap(Object value) {
        return (Map<String, Object>) value;
    }
}
