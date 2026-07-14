package com.roombooking.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import module java.base;

public record Booking(String id, Room room, Person organiser, List<Person> attendees, String subject, String startTime, String endTime) {

    public Map<String, AttributeValue> toItem() {
        final Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id).build());
        item.put("room", AttributeValue.builder().m(room.toItem()).build());
        item.put("organiser", AttributeValue.builder().m(organiser.toItem()).build());
        item.put("attendees", AttributeValue.builder()
                .l(attendees.stream()
                        .map(person -> AttributeValue.builder().m(person.toItem()).build())
                        .toList())
                .build());
        item.put("subject", AttributeValue.builder().s(subject).build());
        item.put("startTime", AttributeValue.builder().s(startTime).build());
        item.put("endTime", AttributeValue.builder().s(endTime).build());
        return item;
    }

    public Map<String, Object> toResponseMap() {
        final Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("room", room.toResponseMap());
        map.put("organiser", organiser.toResponseMap());
        map.put("attendees", attendees.stream().map(Person::toResponseMap).toList());
        map.put("subject", subject);
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        return map;
    }

    public static Booking fromItem(final Map<String, AttributeValue> item) {
        final List<Person> attendees = item.get("attendees").l().stream()
                .map(av -> Person.fromItem(av.m()))
                .toList();
        return new Booking(
                item.get("id").s(),
                Room.fromItem(item.get("room").m()),
                Person.fromItem(item.get("organiser").m()),
                attendees,
                item.get("subject").s(),
                item.get("startTime").s(),
                item.get("endTime").s());
    }
}
