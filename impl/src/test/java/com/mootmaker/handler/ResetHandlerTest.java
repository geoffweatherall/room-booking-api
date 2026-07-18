package com.mootmaker.handler;

import com.mootmaker.model.MeetingParticipant;
import com.mootmaker.model.MeetingRecord;
import com.mootmaker.model.Person;
import com.mootmaker.model.Room;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetHandlerTest {

    private static final Map<String, Object> AUTHENTICATED_EVENT = Map.of("identity", Map.of("sub", "test-user"));

    @Test
    void deletesAllRoomsAndMeetingsAndUnlinkedPeople() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        fakeClient.tables.put("Rooms", new ArrayList<>(List.of(
                new Room("room-1", "Conference A", 8).toItem(),
                new Room("room-2", "Conference B", 4).toItem())));
        fakeClient.tables.put("People", new ArrayList<>(List.of(
                new Person("person-1", "Ada Lovelace").toItem())));
        final MeetingRecord meeting = new MeetingRecord("meeting-1", "room-1", "person-1", List.of(), "Weekly sync",
                "2026-07-01T14:30:00", "2026-07-01T15:00:00");
        fakeClient.tables.put("Meetings", new ArrayList<>(List.of(meeting.toItem())));
        fakeClient.tables.put("MeetingParticipants", new ArrayList<>(
                MeetingParticipant.allFor(meeting).stream().map(MeetingParticipant::toItem).toList()));

        final ResetHandler handler = new ResetHandler(fakeClient, "Rooms", "People", "Meetings", "MeetingParticipants");

        final Object result = handler.handleRequest(AUTHENTICATED_EVENT, null);

        assertEquals(Boolean.TRUE, result);
        assertTrue(fakeClient.tables.get("Rooms").isEmpty());
        assertTrue(fakeClient.tables.get("People").isEmpty());
        assertTrue(fakeClient.tables.get("Meetings").isEmpty());
        assertTrue(fakeClient.tables.get("MeetingParticipants").isEmpty());
    }

    @Test
    void keepsPeopleLinkedToACognitoAccount() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        fakeClient.tables.put("People", new ArrayList<>(List.of(
                new Person("guest-1", "Ada Lovelace").toItem(),
                new Person("linked-1", "Grace Hopper", "cognito-sub-123").toItem())));

        final ResetHandler handler = new ResetHandler(fakeClient, "Rooms", "People", "Meetings", "MeetingParticipants");

        final Object result = handler.handleRequest(AUTHENTICATED_EVENT, null);

        assertEquals(Boolean.TRUE, result);
        final List<Map<String, AttributeValue>> remaining = fakeClient.tables.get("People");
        assertEquals(1, remaining.size());
        assertEquals("linked-1", remaining.getFirst().get("id").s());
    }

    @Test
    void succeedsWhenTablesAreAlreadyEmpty() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        final ResetHandler handler = new ResetHandler(fakeClient, "Rooms", "People", "Meetings", "MeetingParticipants");

        final Object result = handler.handleRequest(AUTHENTICATED_EVENT, null);

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void rejectsUnauthenticatedRequests() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        fakeClient.tables.put("Rooms", new ArrayList<>(List.of(new Room("room-1", "Conference A", 8).toItem())));
        final ResetHandler handler = new ResetHandler(fakeClient, "Rooms", "People", "Meetings", "MeetingParticipants");

        assertThrows(IllegalStateException.class, () -> handler.handleRequest(Map.of(), null));
        assertEquals(1, fakeClient.tables.get("Rooms").size());
    }
}
