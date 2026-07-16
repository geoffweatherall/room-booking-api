package com.roombooking.model;

import module java.base;

/**
 * A booking with room, organiser, and attendees fully resolved, for building a GraphQL response.
 * See {@link BookingRecord} for the persisted, id-only shape stored in DynamoDB.
 */
public record Booking(String id, Room room, Person organiser, List<Person> attendees, String subject, String startTime, String endTime) {

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
}
