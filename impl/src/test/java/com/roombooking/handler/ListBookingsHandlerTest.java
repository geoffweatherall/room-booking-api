package com.roombooking.handler;

import com.roombooking.model.BookingRecord;
import com.roombooking.model.Person;
import com.roombooking.model.Room;
import org.junit.jupiter.api.Test;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListBookingsHandlerTest {

    private static final Map<String, Object> AUTHENTICATED_EVENT = Map.of("identity", Map.of("sub", "test-user"));

    @Test
    void returnsAllBookingsWithRoomAndPeopleResolvedFromTheirIds() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        fakeClient.tables.put("Rooms", List.of(new Room("r1", "Conference A", 8).toItem()));
        fakeClient.tables.put("People", List.of(
                new Person("p1", "Ada Lovelace").toItem(),
                new Person("p2", "Alan Turing").toItem()));
        final BookingRecord record = new BookingRecord(
                "1", "r1", "p1", List.of("p2"), "Weekly sync", "2026-07-01T14:30:00", "2026-07-01T15:00:00");
        fakeClient.tables.put("Bookings", List.of(record.toItem()));

        final ListBookingsHandler handler = new ListBookingsHandler(fakeClient, "Bookings", "Rooms", "People");

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> result = (List<Map<String, Object>>) handler.handleRequest(AUTHENTICATED_EVENT, null);

        assertEquals(1, result.size());
        final Map<String, Object> resultBooking = result.getFirst();
        assertEquals("Weekly sync", resultBooking.get("subject"));
        assertEquals("2026-07-01T14:30:00", resultBooking.get("startTime"));
        assertEquals("2026-07-01T15:00:00", resultBooking.get("endTime"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> resultRoom = (Map<String, Object>) resultBooking.get("room");
        assertEquals("Conference A", resultRoom.get("name"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> resultOrganiser = (Map<String, Object>) resultBooking.get("organiser");
        assertEquals("Ada Lovelace", resultOrganiser.get("name"));

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> resultAttendees = (List<Map<String, Object>>) resultBooking.get("attendees");
        assertEquals(1, resultAttendees.size());
        assertEquals("Alan Turing", resultAttendees.getFirst().get("name"));
    }

    @Test
    void resolvesTheSamePersonOrRoomOnlyOnceAcrossMultipleBookings() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        fakeClient.tables.put("Rooms", List.of(new Room("r1", "Conference A", 8).toItem()));
        fakeClient.tables.put("People", List.of(
                new Person("p1", "Ada Lovelace").toItem(),
                new Person("p2", "Alan Turing").toItem()));
        fakeClient.tables.put("Bookings", List.of(
                new BookingRecord("1", "r1", "p1", List.of("p2"), "Weekly sync",
                        "2026-07-01T14:30:00", "2026-07-01T15:00:00").toItem(),
                new BookingRecord("2", "r1", "p2", List.of("p1"), "Follow-up",
                        "2026-07-01T15:00:00", "2026-07-01T15:30:00").toItem()));

        final ListBookingsHandler handler = new ListBookingsHandler(fakeClient, "Bookings", "Rooms", "People");

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> result = (List<Map<String, Object>>) handler.handleRequest(AUTHENTICATED_EVENT, null);

        assertEquals(2, result.size());
        @SuppressWarnings("unchecked")
        final Map<String, Object> firstOrganiser = (Map<String, Object>) result.get(0).get("organiser");
        @SuppressWarnings("unchecked")
        final Map<String, Object> secondOrganiser = (Map<String, Object>) result.get(1).get("organiser");
        assertEquals("Ada Lovelace", firstOrganiser.get("name"));
        assertEquals("Alan Turing", secondOrganiser.get("name"));
    }

    @Test
    void returnsEmptyListWhenTableIsEmpty() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        final ListBookingsHandler handler = new ListBookingsHandler(fakeClient, "Bookings", "Rooms", "People");

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> result = (List<Map<String, Object>>) handler.handleRequest(AUTHENTICATED_EVENT, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsUnauthenticatedRequests() {
        final ListBookingsHandler handler = new ListBookingsHandler(new FakeDynamoDbClient(), "Bookings", "Rooms", "People");

        assertThrows(IllegalStateException.class, () -> handler.handleRequest(Map.of(), null));
    }
}
