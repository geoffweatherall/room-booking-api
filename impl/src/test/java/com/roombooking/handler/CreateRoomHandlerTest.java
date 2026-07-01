package com.roombooking.handler;

import com.roombooking.model.Room;
import org.junit.jupiter.api.Test;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CreateRoomHandlerTest {

    @Test
    void createsRoomAndPersistsIt() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        final CreateRoomHandler handler = new CreateRoomHandler(fakeClient, "Rooms");

        final Map<String, Object> roomInput = new HashMap<>();
        roomInput.put("name", "Conference A");
        roomInput.put("capacity", 8);
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("room", roomInput);
        final Map<String, Object> event = new HashMap<>();
        event.put("arguments", arguments);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) handler.handleRequest(event, null);

        assertNotNull(result.get("id"));
        assertEquals("Conference A", result.get("name"));
        assertEquals(8, result.get("capacity"));
        assertEquals(1, fakeClient.tables.get("Rooms").size());

        final Room persisted = Room.fromItem(fakeClient.tables.get("Rooms").getFirst());
        assertEquals(result.get("id"), persisted.id());
        assertEquals("Conference A", persisted.name());
        assertEquals(8, persisted.capacity());
    }
}
