package com.roombooking.verify;

import com.fasterxml.jackson.databind.JsonNode;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import module java.base;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Acceptance tests for the {@code createBooking} validation rules: 5-minute time boundaries,
 * room capacity, and room double-booking (with overlap edge cases).
 */
class CreateBookingValidationAcceptanceIT {

    private static final Logger LOG = LoggerFactory.getLogger(CreateBookingValidationAcceptanceIT.class);

    private static final String CREATE_ROOM_MUTATION =
            "mutation CreateRoom($room: RoomInput!) { createRoom(room: $room) { room { id } errors } }";
    private static final String CREATE_PERSON_MUTATION =
            "mutation CreatePerson($person: PersonInput!) { createPerson(person: $person) { id } }";
    private static final String CREATE_BOOKING_MUTATION =
            "mutation CreateBooking($booking: BookingInput!) { createBooking(booking: $booking) { booking { id } errors } }";

    private static final int ROOM_CAPACITY = 5;

    private static GraphQlClient client;
    private static Faker faker;

    private String roomName;
    private String roomId;
    private String organiserId;
    private String attendeeId;

    @BeforeAll
    static void setUpClient() {
        client = GraphQlClient.fromEnvironment();
        faker = new Faker();
    }

    @BeforeEach
    void resetDatabaseAndCreateFixtures() {
        client.execute("mutation { reset }");
        roomName = faker.address().city() + " Room";
        roomId = createRoom(roomName, ROOM_CAPACITY);
        organiserId = createPerson(faker.name().fullName());
        attendeeId = createPerson(faker.name().fullName());
        LOG.info("Fixtures ready: room '{}' ({}), organiser {}, attendee {}", roomName, roomId, organiserId, attendeeId);
    }

    @Test
    void startTimeNotOnFiveMinuteBoundaryIsRejected() {
        LOG.info("Checking startTime not aligned to a 5 minute boundary is rejected");
        final JsonNode payload = createBookingPayload(roomId, organiserId, List.of(attendeeId),
                "2026-09-01T10:02:00", "2026-09-01T10:30:00");

        assertThat(bookingOf(payload).isNull(), is(true));
        assertThat(errorsOf(payload), hasItem(equalTo(BookingError.StartMissaligned.name())));
    }

    @Test
    void endTimeNotOnFiveMinuteBoundaryIsRejected() {
        LOG.info("Checking endTime not aligned to a 5 minute boundary is rejected");
        final JsonNode payload = createBookingPayload(roomId, organiserId, List.of(attendeeId),
                "2026-09-01T10:00:00", "2026-09-01T10:33:00");

        assertThat(bookingOf(payload).isNull(), is(true));
        assertThat(errorsOf(payload), hasItem(equalTo(BookingError.EndMissaligned.name())));
    }

    @Test
    void startTimeWithNonZeroSecondsIsRejected() {
        LOG.info("Checking startTime with non-zero seconds is rejected even though the minute is on a 5 minute boundary");
        final JsonNode payload = createBookingPayload(roomId, organiserId, List.of(attendeeId),
                "2026-09-01T10:00:30", "2026-09-01T10:30:00");

        assertThat(bookingOf(payload).isNull(), is(true));
        assertThat(errorsOf(payload), hasItem(equalTo(BookingError.StartMissaligned.name())));
    }

    @Test
    void blankRoomIdIsRejected() {
        LOG.info("Checking a blank roomId is rejected");
        final JsonNode payload = createBookingPayload("", organiserId, List.of(attendeeId),
                "2026-09-01T10:00:00", "2026-09-01T10:30:00");

        assertThat(bookingOf(payload).isNull(), is(true));
        assertThat(errorsOf(payload), hasItem(equalTo(BookingError.RoomRequired.name())));
        assertThat(errorsOf(payload), not(hasItem(equalTo(BookingError.RoomNotFound.name()))));
    }

    @Test
    void blankOrganiserIdIsRejected() {
        LOG.info("Checking a blank organiserId is rejected");
        final JsonNode payload = createBookingPayload(roomId, "", List.of(attendeeId),
                "2026-09-01T10:00:00", "2026-09-01T10:30:00");

        assertThat(bookingOf(payload).isNull(), is(true));
        assertThat(errorsOf(payload), hasItem(equalTo(BookingError.OrganiserRequired.name())));
        assertThat(errorsOf(payload), not(hasItem(equalTo(BookingError.OrganiserNotFound.name()))));
    }

    @Test
    void insufficientRoomCapacityIsRejected() {
        LOG.info("Checking a booking exceeding room capacity is rejected");
        final List<String> attendeeIds = new ArrayList<>();
        for (int i = 0; i < ROOM_CAPACITY; i++) {
            attendeeIds.add(createPerson(faker.name().fullName()));
        }
        // organiser + ROOM_CAPACITY attendees = ROOM_CAPACITY + 1 people, one more than the room holds.
        final JsonNode payload = createBookingPayload(roomId, organiserId, attendeeIds,
                "2026-09-01T10:00:00", "2026-09-01T10:30:00");

        assertThat(bookingOf(payload).isNull(), is(true));
        assertThat(errorsOf(payload), hasItem(equalTo(BookingError.InsufficientCapacity.name())));
    }

    @Test
    void identicalTimeRangeOverlapIsRejected() {
        createExistingBooking("2026-09-01T10:00:00", "2026-09-01T11:00:00");

        LOG.info("Checking an identical time range for the same room is rejected");
        assertOverlapRejected("2026-09-01T10:00:00", "2026-09-01T11:00:00");
    }

    @Test
    void newBookingFullyContainingExistingBookingIsRejected() {
        // Existing booking is a short window nested inside the new, larger request.
        createExistingBooking("2026-09-01T10:15:00", "2026-09-01T10:45:00");

        LOG.info("Checking a new booking that fully contains a smaller existing booking is rejected");
        assertOverlapRejected("2026-09-01T10:00:00", "2026-09-01T11:00:00");
    }

    @Test
    void newBookingFullyContainedWithinExistingBookingIsRejected() {
        // Existing booking is a large window; the new request is a short window nested inside it.
        createExistingBooking("2026-09-01T10:00:00", "2026-09-01T11:00:00");

        LOG.info("Checking a new booking fully contained within a larger existing booking is rejected");
        assertOverlapRejected("2026-09-01T10:15:00", "2026-09-01T10:45:00");
    }

    @Test
    void newBookingOverlappingStartOfExistingBookingIsRejected() {
        createExistingBooking("2026-09-01T10:30:00", "2026-09-01T11:00:00");

        LOG.info("Checking a new booking that starts before and ends during an existing booking is rejected");
        assertOverlapRejected("2026-09-01T10:00:00", "2026-09-01T10:45:00");
    }

    @Test
    void newBookingOverlappingEndOfExistingBookingIsRejected() {
        createExistingBooking("2026-09-01T10:00:00", "2026-09-01T10:30:00");

        LOG.info("Checking a new booking that starts during and ends after an existing booking is rejected");
        assertOverlapRejected("2026-09-01T10:15:00", "2026-09-01T11:00:00");
    }

    @Test
    void bookingImmediatelyAfterExistingBookingIsAllowed() {
        createExistingBooking("2026-09-01T10:00:00", "2026-09-01T10:30:00");

        LOG.info("Checking a new booking starting exactly when an existing booking ends is allowed (back-to-back)");
        assertOverlapAllowed("2026-09-01T10:30:00", "2026-09-01T11:00:00");
    }

    @Test
    void bookingImmediatelyBeforeExistingBookingIsAllowed() {
        createExistingBooking("2026-09-01T10:30:00", "2026-09-01T11:00:00");

        LOG.info("Checking a new booking ending exactly when an existing booking starts is allowed (back-to-back)");
        assertOverlapAllowed("2026-09-01T10:00:00", "2026-09-01T10:30:00");
    }

    @Test
    void nonOverlappingBookingForSameRoomIsAllowed() {
        createExistingBooking("2026-09-01T10:00:00", "2026-09-01T10:30:00");

        LOG.info("Checking a clearly separate time range for the same room is allowed");
        assertOverlapAllowed("2026-09-01T10:45:00", "2026-09-01T11:00:00");
    }

    @Test
    void overlappingTimeRangeForDifferentRoomIsAllowed() {
        createExistingBooking("2026-09-01T10:00:00", "2026-09-01T11:00:00");

        LOG.info("Checking the identical time range is allowed when booked against a different room");
        final String otherRoomId = createRoom(faker.address().city() + " Room", ROOM_CAPACITY);
        final JsonNode payload = createBookingPayload(otherRoomId, organiserId, List.of(attendeeId),
                "2026-09-01T10:00:00", "2026-09-01T11:00:00");

        assertThat(errorsOf(payload), is(empty()));
        assertThat(bookingOf(payload).isNull(), is(false));
    }

    @Test
    void multipleValidationErrorsAreAllReturnedTogetherInOneRequest() {
        LOG.info("Checking that unrelated validation failures (missing organiser, insufficient capacity, "
                + "unavailable time range) are all reported together rather than stopping at the first one");
        createExistingBooking("2026-09-01T10:00:00", "2026-09-01T11:00:00");

        final List<String> tooManyAttendees = new ArrayList<>();
        for (int i = 0; i < ROOM_CAPACITY; i++) {
            tooManyAttendees.add(createPerson(faker.name().fullName()));
        }

        // organiserId does not exist; attendee count exceeds room capacity; time range overlaps the existing booking.
        final JsonNode payload = createBookingPayload(roomId, "does-not-exist", tooManyAttendees,
                "2026-09-01T10:30:00", "2026-09-01T11:30:00");

        assertThat(bookingOf(payload).isNull(), is(true));
        assertThat(errorsOf(payload), containsInAnyOrder(
                BookingError.OrganiserNotFound.name(),
                BookingError.InsufficientCapacity.name(),
                BookingError.TimeRangeUnavailable.name()));
    }

    private void createExistingBooking(final String startTime, final String endTime) {
        LOG.info("Creating existing booking from {} to {}", startTime, endTime);
        final JsonNode payload = createBookingPayload(roomId, organiserId, List.of(attendeeId), startTime, endTime);
        assertThat("fixture booking must be created successfully", errorsOf(payload), is(empty()));
    }

    private void assertOverlapRejected(final String startTime, final String endTime) {
        final JsonNode payload = createBookingPayload(roomId, organiserId, List.of(attendeeId), startTime, endTime);

        assertThat(bookingOf(payload).isNull(), is(true));
        assertThat(errorsOf(payload), hasItem(equalTo(BookingError.TimeRangeUnavailable.name())));
    }

    private void assertOverlapAllowed(final String startTime, final String endTime) {
        final JsonNode payload = createBookingPayload(roomId, organiserId, List.of(attendeeId), startTime, endTime);

        assertThat(errorsOf(payload), is(empty()));
        assertThat(bookingOf(payload).isNull(), is(false));
    }

    private String createRoom(final String name, final int capacity) {
        final JsonNode result = client.execute(CREATE_ROOM_MUTATION, Map.of("room", Map.of("name", name, "capacity", capacity)));
        return result.get("createRoom").get("room").get("id").asText();
    }

    private String createPerson(final String name) {
        final JsonNode result = client.execute(CREATE_PERSON_MUTATION, Map.of("person", Map.of("name", name)));
        return result.get("createPerson").get("id").asText();
    }

    private JsonNode createBookingPayload(final String roomId, final String organiserId, final List<String> attendeeIds,
            final String startTime, final String endTime) {
        final JsonNode result = client.execute(CREATE_BOOKING_MUTATION, Map.of("booking", Map.of(
                "roomId", roomId,
                "organiserId", organiserId,
                "attendeeIds", attendeeIds,
                "startTime", startTime,
                "endTime", endTime)));
        return result.get("createBooking");
    }

    private static JsonNode bookingOf(final JsonNode createBookingPayload) {
        return createBookingPayload.get("booking");
    }

    private static List<String> errorsOf(final JsonNode createBookingPayload) {
        final List<String> errors = new ArrayList<>();
        createBookingPayload.get("errors").forEach(node -> errors.add(node.asText()));
        return errors;
    }
}
