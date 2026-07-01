package com.roombooking.handler;

import com.roombooking.model.Room;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CreateRoomHandlerTest {

    @Test
    void createsRoomAndPersistsIt() {
        FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        CreateRoomHandler handler = new CreateRoomHandler(fakeClient, "Rooms");

        Map<String, Object> roomInput = new HashMap<>();
        roomInput.put("name", "Conference A");
        roomInput.put("capacity", 8);
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("room", roomInput);
        Map<String, Object> event = new HashMap<>();
        event.put("arguments", arguments);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) handler.handleRequest(event, null);

        assertNotNull(result.get("id"));
        assertEquals("Conference A", result.get("name"));
        assertEquals(8, result.get("capacity"));
        assertEquals(1, fakeClient.tables.get("Rooms").size());

        Room persisted = Room.fromItem(fakeClient.tables.get("Rooms").get(0));
        assertEquals(result.get("id"), persisted.getId());
        assertEquals("Conference A", persisted.getName());
        assertEquals(8, persisted.getCapacity());
    }
}
