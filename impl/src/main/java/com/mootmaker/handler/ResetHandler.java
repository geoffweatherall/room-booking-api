package com.mootmaker.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mootmaker.dynamo.DynamoDbClientProvider;
import com.mootmaker.model.MeetingParticipant;
import com.mootmaker.model.MeetingRecord;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import module java.base;

/**
 * AppSync direct-Lambda resolver for {@code Mutation.reset}. Deletes all stored rooms and
 * meetings, and every person <b>except</b> those linked to a real Cognito account (identified by
 * a non-null {@code cognitoSub}) - so signed-up users never lose the Person record their account
 * is linked to just because someone reset a shared, non-production environment.
 */
public class ResetHandler implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient dynamoDbClient;
    private final String roomsTableName;
    private final String peopleTableName;
    private final String meetingsTableName;
    private final String meetingParticipantsTableName;

    public ResetHandler() {
        this(DynamoDbClientProvider.client(),
                System.getenv().getOrDefault("ROOMS_TABLE_NAME", "Rooms"),
                System.getenv().getOrDefault("PEOPLE_TABLE_NAME", "People"),
                System.getenv().getOrDefault("MEETINGS_TABLE_NAME", "Meetings"),
                System.getenv().getOrDefault("MEETING_PARTICIPANTS_TABLE_NAME", "MeetingParticipants"));
    }

    ResetHandler(final DynamoDbClient dynamoDbClient, final String roomsTableName, final String peopleTableName,
            final String meetingsTableName, final String meetingParticipantsTableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.roomsTableName = roomsTableName;
        this.peopleTableName = peopleTableName;
        this.meetingsTableName = meetingsTableName;
        this.meetingParticipantsTableName = meetingParticipantsTableName;
    }

    @Override
    public Object handleRequest(final Map<String, Object> event, final Context context) {
        Identity.requireAuthenticated(event);

        deleteAllItems(roomsTableName);
        deleteUnlinkedPeople();
        deleteAllMeetingsAndParticipants();
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

    /**
     * meeting-participants is a derived index of the meetings table (see MeetingParticipant), not
     * a source of truth, so every meeting's participant rows are deleted alongside it here -
     * their keys are computed from the meeting item already being read, rather than needing a
     * separate scan of the participants table.
     */
    private void deleteAllMeetingsAndParticipants() {
        final ScanResponse response = dynamoDbClient.scan(ScanRequest.builder().tableName(meetingsTableName).build());
        for (final Map<String, AttributeValue> item : response.items()) {
            final MeetingRecord record = MeetingRecord.fromItem(item);
            for (final MeetingParticipant participant : MeetingParticipant.allFor(record)) {
                dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                        .tableName(meetingParticipantsTableName)
                        .key(Map.of(
                                "personId", AttributeValue.builder().s(participant.personId()).build(),
                                "sortKey", AttributeValue.builder().s(participant.sortKey()).build()))
                        .build());
            }
            dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                    .tableName(meetingsTableName)
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
