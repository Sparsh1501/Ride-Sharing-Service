-- Core entity tables and the append-only trip event log (owned by trip-service)

CREATE TABLE IF NOT EXISTS riders (
    id           VARCHAR(64) PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    email        VARCHAR(128),
    phone        VARCHAR(32),
    rating       NUMERIC(3,2) DEFAULT 5.00,
    total_trips  INTEGER DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS drivers (
    id              VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    email           VARCHAR(128),
    phone           VARCHAR(32),
    vehicle_type    VARCHAR(16) NOT NULL,
    vehicle_number  VARCHAR(32),
    rating          NUMERIC(3,2) DEFAULT 5.00,
    acceptance_rate NUMERIC(4,3) DEFAULT 0.900,
    total_trips     INTEGER DEFAULT 0,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only: rows are never updated or deleted.
CREATE TABLE IF NOT EXISTS trip_events (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id         VARCHAR(64) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type      VARCHAR(32) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_trip_sequence UNIQUE (trip_id, sequence_number)
);

CREATE INDEX IF NOT EXISTS idx_trip_events_trip ON trip_events (trip_id, sequence_number);
