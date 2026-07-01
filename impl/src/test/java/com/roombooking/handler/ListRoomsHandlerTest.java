package com.roombooking.handler;

import com.roombooking.model.Room;
import org.junit.jupiter.api.Test;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListRoomsHandlerTest {

    @Test
    void returnsAllRoomsInTable() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        fakeClient.tables.put("Rooms", List.of(
                new Room("1", "Conference A", 8).toItem(),
                new Room("2", "Conference B", 12).toItem()));

        final ListRoomsHandler handler = new ListRoomsHandler(fakeClient, "Rooms");

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> result = (List<Map<String, Object>>) handler.handleRequest(Map.of(), null);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> "Conference A".equals(r.get("name"))));
        assertTrue(result.stream().anyMatch(r -> "Conference B".equals(r.get("name"))));
    }

    @Test
    void returnsEmptyListWhenTableIsEmpty() {
        final FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        final ListRoomsHandler handler = new ListRoomsHandler(fakeClient, "Rooms");

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> result = (List<Map<String, Object>>) handler.handleRequest(Map.of(), null);

        assertTrue(result.isEmpty());
    }
}
