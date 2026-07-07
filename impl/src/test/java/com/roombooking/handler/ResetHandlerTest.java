package com.roombooking.handler;

import com.roombooking.model.Booking;
import com.roombooking.model.Person;
import com.roombooking.model.Room;
import org.junit.jupiter.api.Test;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetHandlerTest {

    private static final Map<String, Object> AUTHENTICATED_EVENT = Map.of("identity", Map.of("sub", "test-user"));

    @Test
    void deletesAllRoomsPeopleAndBookings() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        fakeClient.tables.put("Rooms", new ArrayList<>(List.of(
                new Room("room-1", "Conference A", 8).toItem(),
                new Room("room-2", "Conference B", 4).toItem())));
        fakeClient.tables.put("People", new ArrayList<>(List.of(
                new Person("person-1", "Ada Lovelace").toItem())));
        fakeClient.tables.put("Bookings", new ArrayList<>(List.of(
                new Booking("booking-1", new Room("room-1", "Conference A", 8),
                        new Person("person-1", "Ada Lovelace"), List.of(),
                        "2026-07-01T14:30:00", "2026-07-01T15:00:00").toItem())));

        final ResetHandler handler = new ResetHandler(fakeClient, "Rooms", "People", "Bookings");

        final Object result = handler.handleRequest(AUTHENTICATED_EVENT, null);

        assertEquals(Boolean.TRUE, result);
        assertTrue(fakeClient.tables.get("Rooms").isEmpty());
        assertTrue(fakeClient.tables.get("People").isEmpty());
        assertTrue(fakeClient.tables.get("Bookings").isEmpty());
    }

    @Test
    void succeedsWhenTablesAreAlreadyEmpty() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        final ResetHandler handler = new ResetHandler(fakeClient, "Rooms", "People", "Bookings");

        final Object result = handler.handleRequest(AUTHENTICATED_EVENT, null);

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void rejectsUnauthenticatedRequests() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        fakeClient.tables.put("Rooms", new ArrayList<>(List.of(new Room("room-1", "Conference A", 8).toItem())));
        final ResetHandler handler = new ResetHandler(fakeClient, "Rooms", "People", "Bookings");

        assertThrows(IllegalStateException.class, () -> handler.handleRequest(Map.of(), null));
        assertEquals(1, fakeClient.tables.get("Rooms").size());
    }
}
