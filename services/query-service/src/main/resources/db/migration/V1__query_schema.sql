-- Read-model tables (owned by query-service). Rebuilt by replaying trip_events.

CREATE TABLE IF NOT EXISTS query_trips (
    trip_id          VARCHAR(64) PRIMARY KEY,
    rider_id         VARCHAR(64),
    driver_id        VARCHAR(64),
    status           VARCHAR(32),
    pickup_lat       DOUBLE PRECISION,
    pickup_lng       DOUBLE PRECISION,
    drop_lat         DOUBLE PRECISION,
    drop_lng         DOUBLE PRECISION,
    vehicle_type     VARCHAR(16),
    estimated_fare   DOUBLE PRECISION DEFAULT 0,
    final_fare       DOUBLE PRECISION DEFAULT 0,
    surge_multiplier DOUBLE PRECISION DEFAULT 1.0,
    distance_km      DOUBLE PRECISION DEFAULT 0,
    duration_minutes DOUBLE PRECISION DEFAULT 0,
    created_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_query_trips_rider ON query_trips (rider_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_query_trips_driver ON query_trips (driver_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_query_trips_status ON query_trips (status);

CREATE TABLE IF NOT EXISTS surge_history (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    geohash      VARCHAR(16) NOT NULL,
    multiplier   DOUBLE PRECISION NOT NULL,
    demand_count BIGINT DEFAULT 0,
    supply_count BIGINT DEFAULT 0,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_surge_history_zone ON surge_history (geohash, recorded_at DESC);
