package com.roombooking.dynamo;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Lazily-built singleton, reused across warm Lambda invocations to avoid repeated client setup cost. */
public final class DynamoDbClientProvider {

    private static volatile DynamoDbClient client;

    private DynamoDbClientProvider() {
    }

    public static DynamoDbClient client() {
        if (client == null) {
            synchronized (DynamoDbClientProvider.class) {
                if (client == null) {
                    client = DynamoDbClient.builder().build();
                }
            }
        }
        return client;
    }
}
