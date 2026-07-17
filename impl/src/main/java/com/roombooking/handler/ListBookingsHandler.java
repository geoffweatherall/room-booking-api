package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.BatchLoader;
import com.roombooking.dynamo.DynamoDbClientProvider;
import com.roombooking.model.Booking;
import com.roombooking.model.BookingParticipant;
import com.roombooking.model.BookingRecord;
import com.roombooking.model.Person;
import com.roombooking.model.Room;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import module java.base;

/** AppSync direct-Lambda resolver for {@code Query.bookings}. */
public class ListBookingsHandler implements RequestHandler<Map<String, Object>, Object> {

    private static final String BUCKET_START_TIME_INDEX = "bucket-startTime-index";
    private static final String BUCKET = "ALL";

    private final DynamoDbClient dynamoDbClient;
    private final String bookingsTableName;
    private final String roomsTableName;
    private final String peopleTableName;
    private final String bookingParticipantsTableName;

    public ListBookingsHandler() {
        this(DynamoDbClientProvider.client(),
                System.getenv().getOrDefault("BOOKINGS_TABLE_NAME", "Bookings"),
                System.getenv().getOrDefault("ROOMS_TABLE_NAME", "Rooms"),
                System.getenv().getOrDefault("PEOPLE_TABLE_NAME", "People"),
                System.getenv().getOrDefault("BOOKING_PARTICIPANTS_TABLE_NAME", "BookingParticipants"));
    }

    ListBookingsHandler(final DynamoDbClient dynamoDbClient, final String bookingsTableName, final String roomsTableName,
            final String peopleTableName, final String bookingParticipantsTableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.bookingsTableName = bookingsTableName;
        this.roomsTableName = roomsTableName;
        this.peopleTableName = peopleTableName;
        this.bookingParticipantsTableName = bookingParticipantsTableName;
    }

    /** null fromStartTime/toEndTime means no range filter; same for personId. Never partially null. */
    private record Filter(String fromStartTime, String toEndTime, String personId) {
    }

    @Override
    public Object handleRequest(final Map<String, Object> event, final Context context) {
        Identity.requireAuthenticated(event);

        final List<BookingRecord> records = fetchBookingRecords(parseFilter(event));

        final Set<String> roomIds = records.stream().map(BookingRecord::roomId).collect(Collectors.toSet());
        final Set<String> personIds = records.stream()
                .flatMap(record -> Stream.concat(Stream.of(record.organiserId()), record.attendeeIds().stream()))
                .collect(Collectors.toSet());

        // Rooms and people live in separate tables, so the two lookups run concurrently; within each,
        // BatchLoader deduplicates ids and fans out over BatchGetItem so no room or person is fetched twice.
        final CompletableFuture<Map<String, Room>> roomsById = CompletableFuture
                .supplyAsync(() -> BatchLoader.loadById(dynamoDbClient, roomsTableName, roomIds))
                .thenApply(ListBookingsHandler::toRoomsById);
        final CompletableFuture<Map<String, Person>> peopleById = CompletableFuture
                .supplyAsync(() -> BatchLoader.loadById(dynamoDbClient, peopleTableName, personIds))
                .thenApply(ListBookingsHandler::toPeopleById);

        final Map<String, Room> rooms = roomsById.join();
        final Map<String, Person> people = peopleById.join();

        return records.stream()
                .map(record -> resolve(record, rooms, people))
                .map(Booking::toResponseMap)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Filter parseFilter(final Map<String, Object> event) {
        final Object argumentsObj = event.get("arguments");
        if (!(argumentsObj instanceof Map)) {
            return new Filter(null, null, null);
        }
        final Object filterObj = ((Map<String, Object>) argumentsObj).get("filter");
        if (!(filterObj instanceof Map)) {
            return new Filter(null, null, null);
        }
        final Map<String, Object> filter = (Map<String, Object>) filterObj;
        final String fromStartTime = (String) filter.get("fromStartTime");
        final String toEndTime = (String) filter.get("toEndTime");
        final String personId = (String) filter.get("personId");
        if ((fromStartTime == null) != (toEndTime == null)) {
            throw new IllegalArgumentException("fromStartTime and toEndTime must be supplied together.");
        }
        return new Filter(fromStartTime, toEndTime, personId);
    }

    private List<BookingRecord> fetchBookingRecords(final Filter filter) {
        final boolean hasRange = filter.fromStartTime() != null;
        final boolean hasPerson = filter.personId() != null;

        if (!hasRange && !hasPerson) {
            return scanAllBookings();
        }
        if (hasRange && !hasPerson) {
            return queryByDateRange(filter.fromStartTime(), filter.toEndTime());
        }
        final List<String> bookingIds = hasRange
                ? queryParticipantBookingIds(filter.personId(), filter.fromStartTime(), filter.toEndTime())
                : queryParticipantBookingIds(filter.personId());
        return hydrateBookings(bookingIds);
    }

    /** No filter at all genuinely means "every booking", so a scan is the right tool, not a workaround. */
    private List<BookingRecord> scanAllBookings() {
        final ScanResponse response = dynamoDbClient.scan(ScanRequest.builder().tableName(bookingsTableName).build());
        return response.items().stream().map(BookingRecord::fromItem).toList();
    }

    /**
     * Queries the bucket-startTime-index GSI for every booking starting in [fromStartTime,
     * toEndTime). The low end is bounded at the start of fromStartTime's calendar day rather than
     * fromStartTime itself - a safe bound (not just a heuristic buffer) because every booking is
     * confined to a single calendar day (see BookingError.SpansMultipleDays), so nothing starting
     * before that day could still be running when it begins. DynamoDB only allows a single
     * condition on a sort key, so both ends of that bound go through one BETWEEN in the key
     * condition; BETWEEN is inclusive on both ends, so an item starting exactly at toEndTime is
     * excluded afterwards in Java rather than via a second key-attribute condition. endTime isn't
     * part of this index's key schema, so the precise low-end overlap check (an item that started
     * before fromStartTime but is still running) can go in a FilterExpression - unlike startTime,
     * which DynamoDB rejects in a FilterExpression here since it's this index's sort key.
     */
    private List<BookingRecord> queryByDateRange(final String fromStartTime, final String toEndTime) {
        final String dayStart = startOfDay(fromStartTime);
        final QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(bookingsTableName)
                .indexName(BUCKET_START_TIME_INDEX)
                // "bucket" is a DynamoDB reserved word, so it can't appear literally in an
                // expression - it must go through an ExpressionAttributeNames alias.
                .keyConditionExpression("#bucket = :bucket AND startTime BETWEEN :dayStart AND :toEndTime")
                .filterExpression("endTime > :fromStartTime")
                .expressionAttributeNames(Map.of("#bucket", "bucket"))
                .expressionAttributeValues(Map.of(
                        ":bucket", AttributeValue.builder().s(BUCKET).build(),
                        ":dayStart", AttributeValue.builder().s(dayStart).build(),
                        ":toEndTime", AttributeValue.builder().s(toEndTime).build(),
                        ":fromStartTime", AttributeValue.builder().s(fromStartTime).build()))
                .build());
        return response.items().stream()
                .map(BookingRecord::fromItem)
                .filter(record -> record.startTime().compareTo(toEndTime) < 0)
                .toList();
    }

    /** No date range: every booking this person organises or attends, in chronological (sortKey) order. */
    private List<String> queryParticipantBookingIds(final String personId) {
        final QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(bookingParticipantsTableName)
                .keyConditionExpression("personId = :personId")
                .expressionAttributeValues(Map.of(":personId", AttributeValue.builder().s(personId).build()))
                .build());
        return distinctBookingIds(response);
    }

    /**
     * sortKey is "startTime#bookingId"; a bare startTime string (no "#" suffix) sorts immediately
     * before any real entry starting at that instant, so BETWEEN a bare dayStart and a bare
     * toEndTime gives an inclusive-low/exclusive-high range on the startTime component with no
     * FilterExpression needed for the upper bound. endTime > fromStartTime is still applied as a
     * FilterExpression for exact overlap precision at the low end, for the same reason as
     * queryByDateRange above.
     */
    private List<String> queryParticipantBookingIds(final String personId, final String fromStartTime, final String toEndTime) {
        final String dayStart = startOfDay(fromStartTime);
        final QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(bookingParticipantsTableName)
                .keyConditionExpression("personId = :personId AND sortKey BETWEEN :dayStart AND :toEndTime")
                .filterExpression("endTime > :fromStartTime")
                .expressionAttributeValues(Map.of(
                        ":personId", AttributeValue.builder().s(personId).build(),
                        ":dayStart", AttributeValue.builder().s(dayStart).build(),
                        ":toEndTime", AttributeValue.builder().s(toEndTime).build(),
                        ":fromStartTime", AttributeValue.builder().s(fromStartTime).build()))
                .build());
        return distinctBookingIds(response);
    }

    private static String startOfDay(final String dateTime) {
        return LocalDateTime.parse(dateTime).toLocalDate().atStartOfDay().format(BookingRecord.DATE_TIME_FORMAT);
    }

    private static List<String> distinctBookingIds(final QueryResponse response) {
        return response.items().stream()
                .map(BookingParticipant::fromItem)
                .map(BookingParticipant::bookingId)
                .distinct()
                .toList();
    }

    private List<BookingRecord> hydrateBookings(final List<String> bookingIds) {
        final Map<String, Map<String, AttributeValue>> itemsById =
                BatchLoader.loadById(dynamoDbClient, bookingsTableName, Set.copyOf(bookingIds));
        return itemsById.values().stream().map(BookingRecord::fromItem).toList();
    }

    private static Booking resolve(final BookingRecord record, final Map<String, Room> roomsById, final Map<String, Person> peopleById) {
        final Room room = roomsById.get(record.roomId());
        final Person organiser = peopleById.get(record.organiserId());
        final List<Person> attendees = record.attendeeIds().stream().map(peopleById::get).toList();
        return new Booking(record.id(), room, organiser, attendees, record.subject(), record.startTime(), record.endTime());
    }

    private static Map<String, Room> toRoomsById(final Map<String, Map<String, AttributeValue>> itemsById) {
        return itemsById.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Room.fromItem(entry.getValue())));
    }

    private static Map<String, Person> toPeopleById(final Map<String, Map<String, AttributeValue>> itemsById) {
        return itemsById.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Person.fromItem(entry.getValue())));
    }
}
