package com.roombooking.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Booking {

    private final String id;
    private final Room room;
    private final Person organiser;
    private final List<Person> attendees;
    private final String startTime;
    private final String endTime;

    public Booking(final String id, final Room room, final Person organiser, final List<Person> attendees, final String startTime, final String endTime) {
        this.id = id;
        this.room = room;
        this.organiser = organiser;
        this.attendees = attendees;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public Person getOrganiser() {
        return organiser;
    }

    public List<Person> getAttendees() {
        return attendees;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public Map<String, AttributeValue> toItem() {
        final Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id).build());
        item.put("room", AttributeValue.builder().m(room.toItem()).build());
        item.put("organiser", AttributeValue.builder().m(organiser.toItem()).build());
        item.put("attendees", AttributeValue.builder()
                .l(attendees.stream()
                        .map(person -> AttributeValue.builder().m(person.toItem()).build())
                        .collect(Collectors.toList()))
                .build());
        item.put("startTime", AttributeValue.builder().s(startTime).build());
        item.put("endTime", AttributeValue.builder().s(endTime).build());
        return item;
    }

    public Map<String, Object> toResponseMap() {
        final Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("room", room.toResponseMap());
        map.put("organiser", organiser.toResponseMap());
        map.put("attendees", attendees.stream().map(Person::toResponseMap).collect(Collectors.toList()));
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        return map;
    }

    public static Booking fromItem(final Map<String, AttributeValue> item) {
        final List<Person> attendees = item.get("attendees").l().stream()
                .map(av -> Person.fromItem(av.m()))
                .collect(Collectors.toList());
        return new Booking(
                item.get("id").s(),
                Room.fromItem(item.get("room").m()),
                Person.fromItem(item.get("organiser").m()),
                attendees,
                item.get("startTime").s(),
                item.get("endTime").s());
    }
}
