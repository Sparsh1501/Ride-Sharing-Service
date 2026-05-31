package com.dispatch.trip.command;

/** Commands return only this minimal acknowledgement — never full state (CQRS). */
public record CommandResult(String tripId, String status, String message) {
}
