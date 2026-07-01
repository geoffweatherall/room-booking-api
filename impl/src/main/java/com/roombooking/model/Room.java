package com.roombooking.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

public class Room {

    private final String id;
    private final String name;
    private final int capacity;

    public Room(String id, String name, int capacity) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id).build());
        item.put("name", AttributeValue.builder().s(name).build());
        item.put("capacity", AttributeValue.builder().n(String.valueOf(capacity)).build());
        return item;
    }

    public Map<String, Object> toResponseMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("capacity", capacity);
        return map;
    }

    public static Room fromItem(Map<String, AttributeValue> item) {
        return new Room(
                item.get("id").s(),
                item.get("name").s(),
                Integer.parseInt(item.get("capacity").n()));
    }
}
