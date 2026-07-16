package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.BatchLoader;
import com.roombooking.dynamo.DynamoDbClientProvider;
import com.roombooking.model.Booking;
import com.roombooking.model.BookingRecord;
import com.roombooking.model.Person;
import com.roombooking.model.Room;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import module java.base;

/** AppSync direct-Lambda resolver for {@code Query.bookings}. */
public class ListBookingsHandler implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient dynamoDbClient;
    private final String bookingsTableName;
    private final String roomsTableName;
    private final String peopleTableName;

    public ListBookingsHandler() {
        this(DynamoDbClientProvider.client(),
                System.getenv().getOrDefault("BOOKINGS_TABLE_NAME", "Bookings"),
                System.getenv().getOrDefault("ROOMS_TABLE_NAME", "Rooms"),
                System.getenv().getOrDefault("PEOPLE_TABLE_NAME", "People"));
    }

    ListBookingsHandler(final DynamoDbClient dynamoDbClient, final String bookingsTableName, final String roomsTableName,
            final String peopleTableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.bookingsTableName = bookingsTableName;
        this.roomsTableName = roomsTableName;
        this.peopleTableName = peopleTableName;
    }

    @Override
    public Object handleRequest(final Map<String, Object> event, final Context context) {
        Identity.requireAuthenticated(event);

        final ScanResponse response = dynamoDbClient.scan(ScanRequest.builder().tableName(bookingsTableName).build());
        final List<BookingRecord> records = response.items().stream().map(BookingRecord::fromItem).toList();

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
