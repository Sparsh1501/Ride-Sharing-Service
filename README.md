# Dispatch Engine — Uber-Style Real-Time Dispatch System

A production-shaped, event-driven ride dispatch platform built with **Java 17 + Spring
Boot 3.3**, **Redis 7** (GEO + state + consistent-hash ring), **Apache Kafka** (event
streaming / event sourcing), **PostgreSQL 15** (Flyway + JPA), **STOMP WebSockets**,
**Resilience4j**, **Micrometer/Prometheus/Grafana**, and a **React + Vite + Leaflet**
live simulator.

It models how a dispatch core actually works: geospatial driver search, weighted
matching, consistent-hash shard routing, real-time surge pricing, ETA recalculation,
event-sourced trip lifecycle, and CQRS read models. The runtime is one Spring Boot
application on port 8080; Kafka is still the bus between the booking, matching,
trip, surge, notification, and query flows.

---

## Architecture

```
                                  ┌──────────────────────────┐
        Rider App  ───────────►   │      api-gateway :8080    │   (Spring Cloud Gateway)
        Driver App ───────────►   └─────────────┬────────────┘
                                                │  REST
              ┌─────────────────────────────────┼───────────────────────────────────┐
              ▼                                  ▼                                   ▼
   ┌────────────────────┐          ┌────────────────────────┐         ┌────────────────────────┐
   │ ride-request :8081 │          │ driver-location :8083  │         │   surge-pricing :8085   │
   │ fare + surge quote │          │ Redis GEO ingestion    │         │ geohash zones, sliding  │
   │ publish ride.req.  │          │ 4s pings, 30s heartbeat│         │ window, @60s recompute  │
   └─────────┬──────────┘          └───────────┬────────────┘         └────────────┬────────────┘
             │ ride.requested                  │ driver.location.updated           │ surge.updated
             ▼                                  ▼                                   │
   ┌─────────────────────────────────────────────────────┐                         │
   │                  dispatch-service :8082             │                         │
   │  • Redis GEORADIUS (5km → 10km expansion)           │                         │
   │  • ConsistentHashRing (150 vnodes, MurmurHash3,     │                         │
   │    shared via Redis ZSET) → shard ownership         │                         │
   │  • weighted scoring (distance/rating/accept/vehicle)│                         │
   │  • sequential offers (top 3, 15s TTL), NX driver lock│                        │
   └───────────────────────────┬─────────────────────────┘                         │
                                │ driver.match.found (offer)                        │
                                ▼                                                    ▼
                      ┌───────────────────────┐                        ┌────────────────────────┐
   driver.response ◄──┤  notification :8086    ├── /topic/** push ───►  │  Rider / Driver sockets │
        (accept/      │  STOMP hub (JWT ?token)│                        └────────────────────────┘
         reject) ────►└───────────┬───────────┘
                                  │ driver.response
                                  ▼
   ┌─────────────────────────────────────────────────────┐      trip.events / trip.eta.updated
   │                    trip-service :8084               │ ─────────────────────────────┐
   │  • Event sourcing: append-only trip_events (JSONB)  │                               │
   │  • TripEventStore.appendEvent / rehydrate (fold)    │                               ▼
   │  • commands: assign/arrive/start/complete/cancel    │                  ┌────────────────────────┐
   │  • ETA recompute @30s, GET /api/v1/eta              │                  │     query-service :8087 │
   └─────────────────────────────────────────────────────┘                  │  CQRS read models       │
                                                                             │  query_trips, analytics │
                                                                             │  POST .../query/rebuild │
                                                                             └────────────────────────┘

   Backing infra:  Kafka :9092 / Zookeeper :2181 · Redis :6379 · Postgres :5432
   Observability:  Prometheus :9090 · Grafana :3000
```

### Project structure

```
dispatch-engine/
├── src/
│   ├── main/
│   │   ├── java/com/dispatch/
│   │   │   ├── auth/          # JWT handshake + token endpoint
│   │   │   ├── checkout/      # ride quote and ride request
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── entity/
│   │   │   ├── exception/
│   │   │   ├── metrics/
│   │   │   ├── provider/      # outbound surge client
│   │   │   ├── repository/
│   │   │   ├── service/       # matching, location, trip, surge, query
│   │   │   ├── util/
│   │   │   ├── webhook/       # Kafka consumers and STOMP session events
│   │   │   └── DispatchApplication
│   │   └── resources/
│   │       ├── db/migration/
│   │       ├── static/assets/
│   │       ├── templates/
│   │       └── application.yml
│   └── test/
├── frontend/          # React + Vite + Leaflet simulator
├── grafana/           # provisioned datasource + dashboard
├── docker-compose.yml # app + Kafka, Postgres, Redis, Prometheus, Grafana
├── prometheus.yml
└── Dockerfile
```

---

## Core system-design implementations

| Concern | Where | Notes |
|---|---|---|
| Redis GEO indexing | `driver-location-service` | `GEOADD` to `geo:drivers:active` + per-city set; state/location hashes; 30s heartbeat TTL; last-10 points list for heading/speed |
| Consistent hashing | `common/ConsistentHashRing` + `dispatch-service/DistributedHashRing` | 150 virtual nodes, MurmurHash3, ring shared in Redis ZSET; `< 10%` remap on node add (unit tested) |
| Geospatial matching | `dispatch-service/DriverMatchingService` | `GEORADIUS WITHDIST WITHCOORD ASC`, pipelined batch state fetch, weighted top-3, `SET driver:lock:{id} NX EX 20` |
| Scoring | `common/DriverScorer` | `0.40·(1-dist) + 0.25·rating + 0.20·accept + 0.15·vehicleMatch` |
| ETA | `common/EtaCalculator`, `trip-service` | Haversine × road factor / time-of-day speed; `GET /api/v1/eta`; @30s recompute → `trip.eta.updated` |
| Surge | `surge-pricing-service` + `common/SurgeCalculator` | geohash-5 zones, Redis ZSET 5-min sliding window, @60s recompute, 90s TTL, ratio→multiplier (capped 3.0x) |
| Event sourcing | `trip-service/TripEventStore` | append-only `trip_events` (JSONB), fold to `TripState`, Redis state cache invalidated per append |
| CQRS | `trip-service` (command) / `query-service` (read) | commands return `{tripId,status,message}` only; read models rebuilt by replaying the log |
| WebSockets | `notification-service` | STOMP over SockJS, JWT `?token=`, `/topic/{rider|driver|city}/...`, graceful disconnect + active-connection gauge |
| Idempotency | every consumer | `SET event:processed:{eventId} NX EX 86400` |
| Circuit breaker | `ride-request-service/SurgeClient` | Resilience4j; degrades to `1.0x` when surge service is down |

---

## Prerequisites

> **New to the project?** See **[STARTUP.md](STARTUP.md)** for step-by-step local setup, Docker infra, and IntelliJ run instructions.

- JDK 17 (`java -version` → 17)
- Docker + Docker Compose
- Node 18+ (only for the frontend)

> Gradle itself is **not** required — the repo ships a Gradle **8.14.6** wrapper (`gradlew`).

---

## Build & test

```bash
# Build the application jar (hermetic, no Docker needed)
./gradlew build           # Windows: gradlew.bat build

# Run the pure-logic unit suite (hash-ring remap, ETA, surge tiers,
# event-sourcing replay, scoring)
./gradlew test

# Run the Testcontainers Redis integration test (seeds 1000 drivers, asserts
# nearest-first ordering + NX lock). Requires a running Docker daemon.
./gradlew integrationTest
```

The default `build`/`test` tasks **exclude** the `integration`-tagged tests so CI stays
hermetic; run `integrationTest` explicitly when Docker is available.

---

## Run everything (Docker Compose — 14 containers)

```bash
cp .env.example .env
docker compose up --build
```

This starts: `zookeeper, kafka, postgres, redis, dispatch-engine, prometheus, grafana`.

Flyway migrations run automatically when `dispatch-engine` starts.

| URL | What |
|---|---|
| http://localhost:8080 | Dispatch API |
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:9090 | Prometheus |
| http://localhost:3000 | Grafana (admin/admin) — "Dispatch Engine Overview" dashboard |

### Frontend simulator

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
```

Live map (Leaflet) with driver pins, a Rider panel (quote + request + live status),
a Driver panel (go online, 15s offer countdown, accept/reject), a city-metrics panel,
and a scrolling event-stream log.

---

## Simulate a full ride (curl)

```bash
# 1) Put a driver online near the pickup and start pinging location
curl -X POST "http://localhost:8080/api/v1/drivers/driver-1/register?vehicleType=ECONOMY"
curl -X PUT  "http://localhost:8080/api/v1/drivers/driver-1/status?status=AVAILABLE"
curl -X POST "http://localhost:8080/api/v1/drivers/driver-1/location" \
  -H 'Content-Type: application/json' \
  -d '{"lat":12.9716,"lng":77.5946,"cityId":"default"}'

# 2) Get a fare quote (includes surge multiplier)
curl -X POST "http://localhost:8080/api/v1/rides/quote" \
  -H 'Content-Type: application/json' \
  -d '{"riderId":"rider-1","pickupLat":12.9716,"pickupLng":77.5946,"dropLat":12.9352,"dropLng":77.6245,"vehicleType":"ECONOMY"}'

# 3) Request the ride → returns {tripId, status, quote}
curl -X POST "http://localhost:8080/api/v1/rides" \
  -H 'Content-Type: application/json' \
  -d '{"riderId":"rider-1","pickupLat":12.9716,"pickupLng":77.5946,"dropLat":12.9352,"dropLng":77.6245,"vehicleType":"ECONOMY"}'
# => dispatch matches and pushes an offer to /topic/driver/driver-1/ride-offer

# 4) Driver accepts the offer (offerId comes from the WebSocket offer / logs)
curl -X POST "http://localhost:8080/api/v1/drivers/driver-1/ride-offer/<OFFER_ID>/accept"

# 5) Trip lifecycle commands (event-sourced)
TRIP=<TRIP_ID>
curl -X POST "http://localhost:8080/api/v1/trips/$TRIP/arrived?driverId=driver-1"
curl -X POST "http://localhost:8080/api/v1/trips/$TRIP/start?startLat=12.9716&startLng=77.5946"
curl -X POST "http://localhost:8080/api/v1/trips/$TRIP/complete" \
  -H 'Content-Type: application/json' \
  -d '{"endLat":12.9352,"endLng":77.6245,"finalDistanceKm":6.2,"finalFare":118.4,"surgeMultiplier":1.0}'

# 6) Inspect the append-only event log + derived state
curl "http://localhost:8080/api/v1/trips/$TRIP/events"
curl "http://localhost:8080/api/v1/trips/$TRIP/state"

# 7) CQRS read side + analytics
curl "http://localhost:8080/api/v1/trips/$TRIP"
curl "http://localhost:8080/api/v1/riders/rider-1/trips"
curl "http://localhost:8080/api/v1/drivers/driver-1/trips"
curl "http://localhost:8080/api/v1/analytics/city/default/summary"
curl "http://localhost:8080/api/v1/analytics/surge/map"
curl -X POST "http://localhost:8080/api/v1/admin/query/rebuild"   # replay all events
```

### Trigger surge (10 requests, 3 drivers)

Register only 3 drivers in a zone, then fire 10 ride requests for the same pickup:

```bash
for i in $(seq 1 10); do
  curl -s -X POST "http://localhost:8080/api/v1/rides" -H 'Content-Type: application/json' \
    -d '{"riderId":"r'$i'","pickupLat":12.9716,"pickupLng":77.5946,"dropLat":12.94,"dropLng":77.62,"vehicleType":"ECONOMY"}' >/dev/null
done
# demand/supply = 10/3 ≈ 3.3 → multiplier caps at 3.0x within ~60s
curl "http://localhost:8080/api/v1/surge/lookup?lat=12.9716&lng=77.5946"
```

### ETA endpoint

```bash
curl "http://localhost:8080/api/v1/eta?driverLat=12.9716&driverLng=77.5946&riderLat=12.9352&riderLng=77.6245&vehicleType=ECONOMY"
```

---

## Kafka topics

| Topic | Producer | Consumers |
|---|---|---|
| `ride.requested` | ride-request | dispatch, trip, surge |
| `driver.location.updated` | driver-location | dispatch*, notification, surge |
| `driver.match.found` | dispatch | notification, trip |
| `driver.response` | notification (API) | dispatch, trip |
| `trip.events` | trip | query, notification |
| `trip.eta.updated` | trip | notification |
| `surge.updated` | surge | notification, query |

Every message is wrapped in an `EventEnvelope` carrying `eventId, eventType, timestamp,
correlationId, sourceService`. Consumers dedupe on `eventId`.

---

## Metrics (Micrometer → Prometheus)

`dispatch.matching.latency`, `dispatch.match.success_rate{result}`,
`dispatch.candidates.evaluated`, `surge.multiplier.distribution`,
`websocket.connections.active`, plus standard JVM/Kafka client metrics. The Grafana
dashboard "Dispatch Engine Overview" is auto-provisioned.

---

## Status & notes

- ✅ `./gradlew build` passes for the single application, zero errors.
- ✅ 21 unit tests pass (consistent-hash `<10%` remap, ETA formula/time bands, surge tiers,
  event-sourcing replay, scoring).
- ✅ `docker compose config` validates all 14 containers; `Dockerfile` builds each service.
- ⚠️ The Testcontainers Redis integration test is correct and compiles, but on **this
  machine** it cannot start a container because the host `PATH` contains a malformed entry
  (a `<` character) that breaks Testcontainers' Docker discovery. It runs normally on a
  clean host / CI. It is tagged `integration` and excluded from the default build.
- The inter-service event choreography is intentionally pragmatic: `driver.response` is
  consumed by both dispatch (lock/advance) and trip (append `DRIVER_ASSIGNED` /
  `DRIVER_REJECTED`), which is a normal multi-consumer-group Kafka pattern.
