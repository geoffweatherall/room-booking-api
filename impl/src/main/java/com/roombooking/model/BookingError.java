package com.roombooking.model;

/**
 * Mirrors the GraphQL {@code BookingError} enum. Constant names must match the schema's enum
 * value names exactly, since AppSync serializes/validates enum values as these literal strings.
 */
public enum BookingError {
    StartMissaligned,
    EndMissaligned,
    InsufficientCapacity,
    TimeRangeUnavailable,
    RoomRequired,
    RoomNotFound,
    OrganiserRequired,
    OrganiserNotFound,
    AttendeeNotFound,
    SubjectRequired
}
