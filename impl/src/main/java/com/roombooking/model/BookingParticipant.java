package com.roombooking.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import module java.base;

/**
 * One row per (booking, participant) pair - the organiser or an attendee - in the
 * booking-participants join table. attendeeIds is a list on the booking item, and DynamoDB keys
 * must be scalars, so "which bookings is this person organiser of or an attendee on" can't be
 * answered with a GSI on the bookings table itself; this table exists purely to answer it in a
 * single Query. The bookings table remains the source of truth - CreateBookingHandler writes a
 * booking's participant rows in the same {@code TransactWriteItems} call as the booking itself,
 * and room-booking-tools/database-repair's RebuildBookingParticipantsRepair can regenerate this
 * table from the bookings table if the two ever drift.
 */
public record BookingParticipant(String personId, String bookingId, String startTime, String endTime) {

    /**
     * startTime + "#" + bookingId. startTime must already be in the fixed-width canonical form
     * CreateBookingHandler stores (see its DATE_TIME_FORMAT) for this to sort correctly as a
     * plain string. Appending bookingId keeps the key unique even if two of a person's bookings
     * start at the same instant.
     */
    public String sortKey() {
        return startTime + "#" + bookingId;
    }

    public Map<String, AttributeValue> toItem() {
        return Map.of(
                "personId", AttributeValue.builder().s(personId).build(),
                "sortKey", AttributeValue.builder().s(sortKey()).build(),
                "bookingId", AttributeValue.builder().s(bookingId).build(),
                "startTime", AttributeValue.builder().s(startTime).build(),
                "endTime", AttributeValue.builder().s(endTime).build());
    }

    public static BookingParticipant fromItem(final Map<String, AttributeValue> item) {
        return new BookingParticipant(
                item.get("personId").s(),
                item.get("bookingId").s(),
                item.get("startTime").s(),
                item.get("endTime").s());
    }

    /** The organiser plus every attendee of the given booking, i.e. every row it should have here. */
    public static List<BookingParticipant> allFor(final BookingRecord booking) {
        final List<BookingParticipant> participants = new ArrayList<>();
        participants.add(new BookingParticipant(booking.organiserId(), booking.id(), booking.startTime(), booking.endTime()));
        for (final String attendeeId : booking.attendeeIds()) {
            participants.add(new BookingParticipant(attendeeId, booking.id(), booking.startTime(), booking.endTime()));
        }
        return participants;
    }
}
