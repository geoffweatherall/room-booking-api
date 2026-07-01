package com.roombooking.verify;

import com.fasterxml.jackson.databind.JsonNode;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import module java.base;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/** Acceptance test for creating a room and reading it back via the {@code rooms} query. */
class CreateRoomAcceptanceIT {

    private static final Logger LOG = LoggerFactory.getLogger(CreateRoomAcceptanceIT.class);

    private static GraphQlClient client;
    private static Faker faker;

    @BeforeAll
    static void setUpClient() {
        client = GraphQlClient.fromEnvironment();
        faker = new Faker();
    }

    @Test
    void createdRoomIsReturnedByRoomsQuery() {
        LOG.info("Resetting the database before the test");
        client.execute("mutation { reset }");

        final String roomName = faker.address().city() + " Room";
        final int capacity = faker.number().numberBetween(2, 20);
        LOG.info("Creating room '{}' with capacity {}", roomName, capacity);
        final JsonNode createResult = client.execute(
                "mutation CreateRoom($room: RoomInput!) { createRoom(room: $room) { id name capacity } }",
                Map.of("room", Map.of("name", roomName, "capacity", capacity)));

        final String createdId = createResult.get("createRoom").get("id").asText();
        LOG.info("Created room with id '{}'", createdId);
        assertThat(createResult.get("createRoom").get("name").asText(), equalTo(roomName));
        assertThat(createResult.get("createRoom").get("capacity").asInt(), equalTo(capacity));

        LOG.info("Querying rooms to check the created room is returned");
        final JsonNode roomsResult = client.execute("query { rooms { id name capacity } }");
        final JsonNode rooms = roomsResult.get("rooms");

        assertThat(rooms.size(), equalTo(1));
        assertThat(rooms.get(0).get("id").asText(), equalTo(createdId));
        assertThat(rooms.get(0).get("name").asText(), equalTo(roomName));
        assertThat(rooms.get(0).get("capacity").asInt(), equalTo(capacity));
        LOG.info("Room '{}' was successfully returned by the rooms query", roomName);
    }
}
