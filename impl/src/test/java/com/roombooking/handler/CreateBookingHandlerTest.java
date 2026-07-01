package com.roombooking.handler;

import com.roombooking.model.Booking;
import com.roombooking.model.BookingError;
import com.roombooking.model.Person;
import com.roombooking.model.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateBookingHandlerTest {

    private FakeDynamoDbClient fakeClient;
    private CreateBookingHandler handler;

    @BeforeEach
    void setUp() {
        fakeClient = new FakeDynamoDbClient();
        handler = new CreateBookingHandler(fakeClient, "Rooms", "People", "Bookings");

        fakeClient.tables.put("Rooms", List.of(new Room("room-1", "Conference A", 2).toItem()));
        fakeClient.tables.put("People", List.of(
                new Person("organiser-1", "Ada Lovelace").toItem(),
                new Person("attendee-1", "Alan Turing").toItem(),
                new Person("attendee-2", "Grace Hopper").toItem()));
    }

    private static Map<String, Object> bookingArguments(final String roomId, final String organiserId, final List<String> attendeeIds,
            final String startTime, final String endTime) {
        final Map<String, Object> booking = new HashMap<>();
        booking.put("roomId", roomId);
        booking.put("organiserId", organiserId);
        booking.put("attendeeIds", attendeeIds);
        booking.put("startTime", startTime);
        booking.put("endTime", endTime);
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("booking", booking);
        final Map<String, Object> event = new HashMap<>();
        event.put("arguments", arguments);
        return event;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(final Map<String, Object> event) {
        return (Map<String, Object>) handler.handleRequest(event, null);
    }

    @Test
    void createsBookingWhenAllRulesPass() {
        final Map<String, Object> event = bookingArguments("room-1", "organiser-1", List.of("attendee-1"),
                "2026-07-01T14:30:00", "2026-07-01T15:00:00");

        final Map<String, Object> result = invoke(event);

        @SuppressWarnings("unchecked")
        final List<String> errors = (List<String>) result.get("errors");
        assertTrue(errors.isEmpty());

        @SuppressWarnings("unchecked")
        final Map<String, Object> booking = (Map<String, Object>) result.get("booking");
        assertNotNull(booking);
        assertNotNull(booking.get("id"));
        assertEquals(1, fakeClient.tables.get("Bookings").size());
    }

    @Test
    void rejectsStartOrEndTimeNotOnFiveMinuteBoundary() {
        final Map<String, Object> event = bookingArguments("room-1", "organiser-1", List.of("attendee-1"),
                "2026-07-01T14:32:00", "2026-07-01T15:00:00");

        final Map<String, Object> result = invoke(event);

        @SuppressWarnings("unchecked")
        final List<String> errors = (List<String>) result.get("errors");
        assertTrue(errors.contains(BookingError.StartMissaligned.name()));
        assertNull(result.get("booking"));
        assertTrue(fakeClient.tables.getOrDefault("Bookings", List.of()).isEmpty());
    }

    @Test
    void rejectsWhenRoomCapacityInsufficient() {
        final Map<String, Object> event = bookingArguments("room-1", "organiser-1",
                List.of("attendee-1", "attendee-2"), "2026-07-01T14:30:00", "2026-07-01T15:00:00");

        final Map<String, Object> result = invoke(event);

        @SuppressWarnings("unchecked")
        final List<String> errors = (List<String>) result.get("errors");
        assertTrue(errors.contains(BookingError.InsufficientCapacity.name()));
        assertNull(result.get("booking"));
    }

    @Test
    void rejectsWhenRoomAlreadyBookedForOverlappingTime() {
        final Booking existing = new Booking("existing-booking", new Room("room-1", "Conference A", 3),
                new Person("organiser-1", "Ada Lovelace"), List.of(),
                "2026-07-01T14:00:00", "2026-07-01T15:00:00");
        fakeClient.tables.put("Bookings", List.of(existing.toItem()));

        final Map<String, Object> event = bookingArguments("room-1", "organiser-1", List.of("attendee-1"),
                "2026-07-01T14:30:00", "2026-07-01T15:30:00");

        final Map<String, Object> result = invoke(event);

        @SuppressWarnings("unchecked")
        final List<String> errors = (List<String>) result.get("errors");
        assertTrue(errors.contains(BookingError.TimeRangeUnavailable.name()));
        assertNull(result.get("booking"));
        assertEquals(1, fakeClient.tables.get("Bookings").size());
    }

    @Test
    void allowsBackToBackBookingsThatDoNotOverlap() {
        final Booking existing = new Booking("existing-booking", new Room("room-1", "Conference A", 3),
                new Person("organiser-1", "Ada Lovelace"), List.of(),
                "2026-07-01T14:00:00", "2026-07-01T14:30:00");
        fakeClient.tables.put("Bookings", new ArrayList<>(List.of(existing.toItem())));

        final Map<String, Object> event = bookingArguments("room-1", "organiser-1", List.of("attendee-1"),
                "2026-07-01T14:30:00", "2026-07-01T15:00:00");

        final Map<String, Object> result = invoke(event);

        @SuppressWarnings("unchecked")
        final List<String> errors = (List<String>) result.get("errors");
        assertTrue(errors.isEmpty());
        assertNotNull(result.get("booking"));
    }
}
