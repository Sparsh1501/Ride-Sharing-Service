package com.dispatch.util;

/**
 * Canonical Kafka topic names shared by producers and consumers.
 */
public final class KafkaTopics {

    public static final String RIDE_REQUESTED = "ride.requested";
    public static final String DRIVER_LOCATION_UPDATED = "driver.location.updated";
    public static final String DRIVER_MATCH_FOUND = "driver.match.found";
    public static final String DRIVER_RESPONSE = "driver.response";
    public static final String TRIP_EVENTS = "trip.events";
    public static final String TRIP_ETA_UPDATED = "trip.eta.updated";
    public static final String SURGE_UPDATED = "surge.updated";

    public static String dlq(String topic) {
        return topic + ".dlq";
    }

    private KafkaTopics() {
    }
}
