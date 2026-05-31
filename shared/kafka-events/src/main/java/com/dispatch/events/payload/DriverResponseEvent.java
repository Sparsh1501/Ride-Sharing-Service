package com.dispatch.events.payload;

public record DriverResponseEvent(
        String tripId,
        String offerId,
        String driverId,
        Response response) {

    public enum Response {
        ACCEPT,
        REJECT,
        TIMEOUT
    }
}
