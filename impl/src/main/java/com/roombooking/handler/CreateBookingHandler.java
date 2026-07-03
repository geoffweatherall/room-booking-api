package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.DynamoDbClientProvider;
import com.roombooking.model.Booking;
import com.roombooking.model.BookingError;
import com.roombooking.model.Person;
import com.roombooking.model.Room;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import module java.base;

/**
 * AppSync direct-Lambda resolver for {@code Mutation.createBooking}.
 * Validates the request and either persists the booking or returns the list of broken rules.
 */
public class CreateBookingHandler implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient dynamoDbClient;
    private final String roomsTableName;
    private final String peopleTableName;
    private final String bookingsTableName;

    public CreateBookingHandler() {
        this(DynamoDbClientProvider.client(),
                System.getenv().getOrDefault("ROOMS_TABLE_NAME", "Rooms"),
                System.getenv().getOrDefault("PEOPLE_TABLE_NAME", "People"),
                System.getenv().getOrDefault("BOOKINGS_TABLE_NAME", "Bookings"));
    }

    CreateBookingHandler(final DynamoDbClient dynamoDbClient, final String roomsTableName, final String peopleTableName,
            final String bookingsTableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.roomsTableName = roomsTableName;
        this.peopleTableName = peopleTableName;
        this.bookingsTableName = bookingsTableName;
    }

    @Override
    public Object handleRequest(final Map<String, Object> event, final Context context) {
        final Map<String, Object> arguments = castToMap(event.get("arguments"));
        final Map<String, Object> bookingInput = castToMap(arguments.get("booking"));

        final String roomId = (String) bookingInput.get("roomId");
        final String organiserId = (String) bookingInput.get("organiserId");
        @SuppressWarnings("unchecked")
        final List<String> attendeeIds = (List<String>) bookingInput.get("attendeeIds");
        final String startTimeText = (String) bookingInput.get("startTime");
        final String endTimeText = (String) bookingInput.get("endTime");

        final List<String> errors = new ArrayList<>();

        final LocalDateTime startTime = parseOnFiveMinuteBoundary(startTimeText, BookingError.StartMissaligned, errors);
        final LocalDateTime endTime = parseOnFiveMinuteBoundary(endTimeText, BookingError.EndMissaligned, errors);

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

        final Booking booking = new Booking(UUID.randomUUID().toString(), room, organiser, attendees, startTimeText, endTimeText);
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(bookingsTableName)
                .item(booking.toItem())
                .build());

        result.put("booking", booking.toResponseMap());
        result.put("errors", errors);
        return result;
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

    private boolean roomHasOverlappingBooking(final String roomId, final LocalDateTime startTime, final LocalDateTime endTime) {
        final ScanResponse response = dynamoDbClient.scan(ScanRequest.builder().tableName(bookingsTableName).build());
        return response.items().stream()
                .map(Booking::fromItem)
                .filter(existing -> existing.room().id().equals(roomId))
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
