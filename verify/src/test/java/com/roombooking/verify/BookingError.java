package com.roombooking.verify;

/**
 * Mirrors the GraphQL {@code BookingError} enum. Constant names must match the schema's enum
 * value names exactly, since that's the literal string AppSync returns over the wire.
 */
enum BookingError {
    StartMissaligned,
    EndMissaligned,
    InsufficientCapacity,
    TimeRangeUnavailable,
    RoomNotFound,
    OrganiserNotFound,
    AttendeeNotFound
}
