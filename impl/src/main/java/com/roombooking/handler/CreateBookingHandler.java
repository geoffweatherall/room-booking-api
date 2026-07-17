package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.DynamoDbClientProvider;
import com.roombooking.model.Booking;
import com.roombooking.model.BookingError;
import com.roombooking.model.BookingParticipant;
import com.roombooking.model.BookingRecord;
import com.roombooking.model.Person;
import com.roombooking.model.Room;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

import module java.base;

/**
 * AppSync direct-Lambda resolver for {@code Mutation.createBooking}.
 * Validates the request and either persists the booking or returns the list of broken rules.
 */
public class CreateBookingHandler implements RequestHandler<Map<String, Object>, Object> {

    private static final String ROOM_START_TIME_INDEX = "roomId-startTime-index";

    private final DynamoDbClient dynamoDbClient;
    private final String roomsTableName;
    private final String peopleTableName;
    private final String bookingsTableName;
    private final String bookingParticipantsTableName;

    public CreateBookingHandler() {
        this(DynamoDbClientProvider.client(),
                System.getenv().getOrDefault("ROOMS_TABLE_NAME", "Rooms"),
                System.getenv().getOrDefault("PEOPLE_TABLE_NAME", "People"),
                System.getenv().getOrDefault("BOOKINGS_TABLE_NAME", "Bookings"),
                System.getenv().getOrDefault("BOOKING_PARTICIPANTS_TABLE_NAME", "BookingParticipants"));
    }

    CreateBookingHandler(final DynamoDbClient dynamoDbClient, final String roomsTableName, final String peopleTableName,
            final String bookingsTableName, final String bookingParticipantsTableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.roomsTableName = roomsTableName;
        this.peopleTableName = peopleTableName;
        this.bookingsTableName = bookingsTableName;
        this.bookingParticipantsTableName = bookingParticipantsTableName;
    }

    @Override
    public Object handleRequest(final Map<String, Object> event, final Context context) {
        Identity.requireAuthenticated(event);

        final Map<String, Object> arguments = castToMap(event.get("arguments"));
        final Map<String, Object> bookingInput = castToMap(arguments.get("booking"));

        final String roomId = (String) bookingInput.get("roomId");
        final String organiserId = (String) bookingInput.get("organiserId");
        @SuppressWarnings("unchecked")
        final List<String> attendeeIds = (List<String>) bookingInput.get("attendeeIds");
        final String subject = (String) bookingInput.get("subject");
        final String startTimeText = (String) bookingInput.get("startTime");
        final String endTimeText = (String) bookingInput.get("endTime");

        final List<String> errors = new ArrayList<>();

        if (isBlank(subject)) {
            errors.add(BookingError.SubjectRequired.name());
        }

        final LocalDateTime startTime = parseOnFiveMinuteBoundary(startTimeText, BookingError.StartMissaligned, errors);
        final LocalDateTime endTime = parseOnFiveMinuteBoundary(endTimeText, BookingError.EndMissaligned, errors);

        if (startTime != null && endTime != null && !startTime.toLocalDate().equals(endTime.toLocalDate())) {
            errors.add(BookingError.SpansMultipleDays.name());
        }

        Room room = null;
        if (isBlank(roomId)) {
            errors.add(BookingError.RoomRequired.name());
        } else {
            room = getRoom(roomId);
            if (room == null) {
                errors.add(BookingError.RoomNotFound.name());
            }
        }

        Person organiser = null;
        if (isBlank(organiserId)) {
            errors.add(BookingError.OrganiserRequired.name());
        } else {
            organiser = getPerson(organiserId);
            if (organiser == null) {
                errors.add(BookingError.OrganiserNotFound.name());
            }
        }

        final List<Person> attendees = new ArrayList<>();
        for (final String attendeeId : attendeeIds) {
            final Person attendee = getPerson(attendeeId);
            if (attendee == null) {
                errors.add(BookingError.AttendeeNotFound.name());
            } else {
                attendees.add(attendee);
            }
        }

        if (room != null) {
            final int requiredCapacity = 1 + attendeeIds.size();
            if (room.capacity() < requiredCapacity) {
                errors.add(BookingError.InsufficientCapacity.name());
            }
        }

        if (room != null && startTime != null && endTime != null && roomHasOverlappingBooking(roomId, startTime, endTime)) {
            errors.add(BookingError.TimeRangeUnavailable.name());
        }

        final Map<String, Object> result = new HashMap<>();
        if (!errors.isEmpty()) {
            result.put("booking", null);
            result.put("errors", errors);
            return result;
        }

        final String id = UUID.randomUUID().toString();
        final String canonicalStartTime = startTime.format(BookingRecord.DATE_TIME_FORMAT);
        final String canonicalEndTime = endTime.format(BookingRecord.DATE_TIME_FORMAT);
        final BookingRecord record =
                new BookingRecord(id, roomId, organiserId, attendeeIds, subject, canonicalStartTime, canonicalEndTime);
        writeBookingAndParticipants(record);

        final Booking booking = new Booking(id, room, organiser, attendees, subject, canonicalStartTime, canonicalEndTime);
        result.put("booking", booking.toResponseMap());
        result.put("errors", errors);
        return result;
    }

    /**
     * Writes the booking and one booking-participants row per organiser/attendee (see
     * BookingParticipant) in a single transaction, so the participants table - a derived index,
     * not a source of truth - can never end up missing rows for a booking that was actually
     * created.
     */
    private void writeBookingAndParticipants(final BookingRecord record) {
        final List<TransactWriteItem> transactItems = new ArrayList<>();
        transactItems.add(TransactWriteItem.builder()
                .put(Put.builder().tableName(bookingsTableName).item(record.toItem()).build())
                .build());
        for (final BookingParticipant participant : BookingParticipant.allFor(record)) {
            transactItems.add(TransactWriteItem.builder()
                    .put(Put.builder().tableName(bookingParticipantsTableName).item(participant.toItem()).build())
                    .build());
        }
        dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(transactItems).build());
    }

    private static LocalDateTime parseOnFiveMinuteBoundary(final String text, final BookingError error, final List<String> errors) {
        if (text == null) {
            errors.add(error.name());
            return null;
        }
        final LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.parse(text);
        } catch (final DateTimeParseException _) {
            errors.add(error.name());
            return null;
        }
        if (dateTime.getSecond() != 0 || dateTime.getNano() != 0 || dateTime.getMinute() % 5 != 0) {
            errors.add(error.name());
            return null;
        }
        return dateTime;
    }

    private Room getRoom(final String roomId) {
        if (isBlank(roomId)) {
            return null;
        }
        final GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(roomsTableName)
                .key(Map.of("id", AttributeValue.builder().s(roomId).build()))
                .build());
        return response.hasItem() ? Room.fromItem(response.item()) : null;
    }

    private Person getPerson(final String personId) {
        if (isBlank(personId)) {
            return null;
        }
        final GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(peopleTableName)
                .key(Map.of("id", AttributeValue.builder().s(personId).build()))
                .build());
        return response.hasItem() ? Person.fromItem(response.item()) : null;
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    /**
     * Queries the roomId-startTime-index GSI for this room's bookings on the requested date -
     * begins_with is exact rather than approximate because every booking is confined to a single
     * calendar day (see the SpansMultipleDays rule above), so two bookings for the same room can
     * only possibly overlap if they share a date - then checks the (small) result set for an
     * actual time overlap. Replaces a full table scan.
     */
    private boolean roomHasOverlappingBooking(final String roomId, final LocalDateTime startTime, final LocalDateTime endTime) {
        final String datePrefix = startTime.toLocalDate().toString();
        final QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(bookingsTableName)
                .indexName(ROOM_START_TIME_INDEX)
                .keyConditionExpression("roomId = :roomId AND begins_with(startTime, :datePrefix)")
                .expressionAttributeValues(Map.of(
                        ":roomId", AttributeValue.builder().s(roomId).build(),
                        ":datePrefix", AttributeValue.builder().s(datePrefix).build()))
                .build());
        return response.items().stream()
                .map(BookingRecord::fromItem)
                .anyMatch(existing -> {
                    final LocalDateTime existingStart = LocalDateTime.parse(existing.startTime());
                    final LocalDateTime existingEnd = LocalDateTime.parse(existing.endTime());
                    return startTime.isBefore(existingEnd) && endTime.isAfter(existingStart);
                });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToMap(final Object value) {
        return (Map<String, Object>) value;
    }
}
