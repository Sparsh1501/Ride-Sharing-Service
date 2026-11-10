package com.dispatch.service;

import com.dispatch.entity.TripEventType;
import com.dispatch.util.JsonUtils;
import com.dispatch.dto.TripEventMessage;
import com.dispatch.repository.QueryTripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rebuilds all read models from scratch by replaying the append-only
 * {@code trip_events} log. This is the safety net of an event-sourced system:
 * the read side can always be regenerated from the source of truth.
 */
@Service
public class RebuildService {

    private static final Logger log = LoggerFactory.getLogger(RebuildService.class);

    private final JdbcTemplate jdbc;
    private final TripProjection projection;
    private final QueryTripRepository repository;

    public RebuildService(JdbcTemplate jdbc, TripProjection projection, QueryTripRepository repository) {
        this.jdbc = jdbc;
        this.projection = projection;
        this.repository = repository;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public int rebuild() {
        repository.deleteAllInBatch();
        AtomicInteger replayed = new AtomicInteger();

        jdbc.query("SELECT trip_id, sequence_number, event_type, payload::text AS payload "
                + "FROM trip_events ORDER BY trip_id, sequence_number", rs -> {
            String tripId = rs.getString("trip_id");
            long seq = rs.getLong("sequence_number");
            TripEventType type = TripEventType.valueOf(rs.getString("event_type"));
            Map<String, Object> payload = JsonUtils.fromJson(rs.getString("payload"), Map.class);
            projection.apply(new TripEventMessage(tripId, seq, type, payload));
            replayed.incrementAndGet();
        });

        log.info("Rebuilt read models by replaying {} trip events", replayed.get());
        return replayed.get();
    }
}
