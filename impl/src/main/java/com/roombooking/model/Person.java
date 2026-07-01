package com.roombooking.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

public class Person {

    private final String id;
    private final String name;

    public Person(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id).build());
        item.put("name", AttributeValue.builder().s(name).build());
        return item;
    }

    public Map<String, Object> toResponseMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        return map;
    }

    public static Person fromItem(Map<String, AttributeValue> item) {
        return new Person(item.get("id").s(), item.get("name").s());
    }
}
