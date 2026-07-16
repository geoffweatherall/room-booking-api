package com.roombooking.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import module java.base;

/**
 * The shape a booking is persisted in DynamoDB: room, organiser, and attendees are referenced by
 * id only. See {@link Booking} for the fully-resolved shape (room/organiser/attendees looked up)
 * used to build a GraphQL response.
 */
public record BookingRecord(String id, String roomId, String organiserId, List<String> attendeeIds, String subject, String startTime,
        String endTime) {

    public Map<String, AttributeValue> toItem() {
        final Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id).build());
        item.put("roomId", AttributeValue.builder().s(roomId).build());
        item.put("organiserId", AttributeValue.builder().s(organiserId).build());
        item.put("attendeeIds", AttributeValue.builder()
                .l(attendeeIds.stream().map(attendeeId -> AttributeValue.builder().s(attendeeId).build()).toList())
                .build());
        item.put("subject", AttributeValue.builder().s(subject).build());
        item.put("startTime", AttributeValue.builder().s(startTime).build());
        item.put("endTime", AttributeValue.builder().s(endTime).build());
        return item;
    }

    public static BookingRecord fromItem(final Map<String, AttributeValue> item) {
        final List<String> attendeeIds = item.get("attendeeIds").l().stream().map(AttributeValue::s).toList();
        return new BookingRecord(
                item.get("id").s(),
                item.get("roomId").s(),
                item.get("organiserId").s(),
                attendeeIds,
                item.get("subject").s(),
                item.get("startTime").s(),
                item.get("endTime").s());
    }
}
