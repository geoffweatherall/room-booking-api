package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.DynamoDbClientProvider;
import com.roombooking.model.Booking;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** AppSync direct-Lambda resolver for {@code Query.bookings}. */
public class ListBookingsHandler implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public ListBookingsHandler() {
        this(DynamoDbClientProvider.client(), System.getenv().getOrDefault("BOOKINGS_TABLE_NAME", "Bookings"));
    }

    ListBookingsHandler(final DynamoDbClient dynamoDbClient, final String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public Object handleRequest(final Map<String, Object> event, final Context context) {
        final ScanResponse response = dynamoDbClient.scan(ScanRequest.builder().tableName(tableName).build());
        final List<Map<String, Object>> bookings = response.items().stream()
                .map(Booking::fromItem)
                .map(Booking::toResponseMap)
                .collect(Collectors.toList());
        return bookings;
    }
}
