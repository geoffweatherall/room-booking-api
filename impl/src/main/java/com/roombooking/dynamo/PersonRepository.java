package com.roombooking.dynamo;

import com.roombooking.model.Person;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import module java.base;

/** Looks up the Person linked to a Cognito user, via the People table's cognitoSub-index GSI. */
public final class PersonRepository {

    private static final String COGNITO_SUB_INDEX = "cognitoSub-index";

    private PersonRepository() {
    }

    public static Optional<Person> findByCognitoSub(
            final DynamoDbClient dynamoDbClient, final String tableName, final String cognitoSub) {
        final List<Map<String, AttributeValue>> items = dynamoDbClient.query(QueryRequest.builder()
                        .tableName(tableName)
                        .indexName(COGNITO_SUB_INDEX)
                        .keyConditionExpression("cognitoSub = :cognitoSub")
                        .expressionAttributeValues(Map.of(":cognitoSub", AttributeValue.builder().s(cognitoSub).build()))
                        .limit(1)
                        .build())
                .items();
        return items.isEmpty() ? Optional.empty() : Optional.of(Person.fromItem(items.getFirst()));
    }
}
