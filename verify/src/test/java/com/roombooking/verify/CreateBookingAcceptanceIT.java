package com.roombooking.verify;

import com.fasterxml.jackson.databind.JsonNode;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/** Acceptance test for creating a booking and reading it back via the {@code bookings} query. */
class CreateBookingAcceptanceIT {

    private static final Logger LOG = LoggerFactory.getLogger(CreateBookingAcceptanceIT.class);

    private static final String CREATE_ROOM_MUTATION =
            "mutation CreateRoom($room: RoomInput!) { createRoom(room: $room) { id name capacity } }";
    private static final String CREATE_PERSON_MUTATION =
            "mutation CreatePerson($person: PersonInput!) { createPerson(person: $person) { id name } }";
    private static final String BOOKING_FIELDS =
            "id room { id name capacity } organiser { id name } attendees { id name } startTime endTime";

    private static GraphQlClient client;
    private static Faker faker;

    @BeforeAll
    static void setUpClient() {
        client = GraphQlClient.fromEnvironment();
        faker = new Faker();
    }

    @Test
    void createdBookingIsReturnedByBookingsQuery() {
        LOG.info("Resetting the database before the test");
        client.execute("mutation { reset }");

        final String roomName = faker.address().city() + " Room";
        LOG.info("Creating room '{}'", roomName);
        final JsonNode roomResult = client.execute(CREATE_ROOM_MUTATION,
                Map.of("room", Map.of("name", roomName, "capacity", 5)));
        final String roomId = roomResult.get("createRoom").get("id").asText();

        final String organiserName = faker.name().fullName();
        LOG.info("Creating organiser '{}'", organiserName);
        final JsonNode organiserResult = client.execute(CREATE_PERSON_MUTATION,
                Map.of("person", Map.of("name", organiserName)));
        final String organiserId = organiserResult.get("createPerson").get("id").asText();

        final String attendeeName = faker.name().fullName();
        LOG.info("Creating attendee '{}'", attendeeName);
        final JsonNode attendeeResult = client.execute(CREATE_PERSON_MUTATION,
                Map.of("person", Map.of("name", attendeeName)));
        final String attendeeId = attendeeResult.get("createPerson").get("id").asText();

        final String startTime = "2026-08-01T10:00:00";
        final String endTime = "2026-08-01T10:30:00";
        LOG.info("Creating booking for room '{}' from {} to {}", roomName, startTime, endTime);
        final JsonNode createBookingResult = client.execute(
                "mutation CreateBooking($booking: BookingInput!) { createBooking(booking: $booking) { booking { "
                        + BOOKING_FIELDS + " } errors } }",
                Map.of("booking", Map.of(
                        "roomId", roomId,
                        "organiserId", organiserId,
                        "attendeeIds", List.of(attendeeId),
                        "startTime", startTime,
                        "endTime", endTime)));

        final JsonNode createBookingPayload = createBookingResult.get("createBooking");
        assertThat(createBookingPayload.get("errors").size(), equalTo(0));

        final JsonNode createdBooking = createBookingPayload.get("booking");
        final String bookingId = createdBooking.get("id").asText();
        LOG.info("Created booking with id '{}'", bookingId);
        assertThat(createdBooking.get("room").get("id").asText(), equalTo(roomId));
        assertThat(createdBooking.get("organiser").get("id").asText(), equalTo(organiserId));
        assertThat(createdBooking.get("attendees").get(0).get("id").asText(), equalTo(attendeeId));
        assertThat(createdBooking.get("startTime").asText(), equalTo(startTime));
        assertThat(createdBooking.get("endTime").asText(), equalTo(endTime));

        LOG.info("Querying bookings to check the created booking is returned");
        final JsonNode bookingsResult = client.execute("query { bookings { " + BOOKING_FIELDS + " } }");
        final JsonNode bookings = bookingsResult.get("bookings");

        assertThat(bookings.size(), equalTo(1));
        assertThat(bookings.get(0).get("id").asText(), equalTo(bookingId));
        assertThat(bookings.get(0).get("room").get("id").asText(), equalTo(roomId));
        assertThat(bookings.get(0).get("organiser").get("id").asText(), equalTo(organiserId));
        assertThat(bookings.get(0).get("attendees").get(0).get("id").asText(), equalTo(attendeeId));
        LOG.info("Booking for room '{}' was successfully returned by the bookings query", roomName);
    }
}
