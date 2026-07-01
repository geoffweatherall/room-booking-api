package com.roombooking.handler;

import com.roombooking.model.Booking;
import com.roombooking.model.Person;
import com.roombooking.model.Room;
import org.junit.jupiter.api.Test;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListBookingsHandlerTest {

    @Test
    void returnsAllBookingsInTableWithNestedRoomAndPeople() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        final Booking booking = new Booking(
                "1",
                new Room("r1", "Conference A", 8),
                new Person("p1", "Ada Lovelace"),
                List.of(new Person("p2", "Alan Turing")),
                "2026-07-01T14:30:00",
                "2026-07-01T15:00:00");
        fakeClient.tables.put("Bookings", List.of(booking.toItem()));

        final ListBookingsHandler handler = new ListBookingsHandler(fakeClient, "Bookings");

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> result = (List<Map<String, Object>>) handler.handleRequest(Map.of(), null);

        assertEquals(1, result.size());
        final Map<String, Object> resultBooking = result.getFirst();
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
    void returnsEmptyListWhenTableIsEmpty() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        final ListBookingsHandler handler = new ListBookingsHandler(fakeClient, "Bookings");

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> result = (List<Map<String, Object>>) handler.handleRequest(Map.of(), null);

        assertTrue(result.isEmpty());
    }
}
