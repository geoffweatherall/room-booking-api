package com.roombooking.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import module java.base;

public record Person(String id, String name) {

    public Map<String, AttributeValue> toItem() {
        final Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id).build());
        item.put("name", AttributeValue.builder().s(name).build());
        return item;
    }

    public Map<String, Object> toResponseMap() {
        final Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        return map;
    }

    public static Person fromItem(final Map<String, AttributeValue> item) {
        return new Person(item.get("id").s(), item.get("name").s());
    }
}
