package com.dispatch.dto;

public record RideResponse(String tripId, String status, String message, RideQuote quote) {
}
