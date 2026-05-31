package com.dispatch.dispatch.match;

import com.dispatch.common.domain.DriverStatus;
import com.dispatch.common.domain.VehicleType;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the geospatial matching path against a real Redis
 * (Testcontainers). Seeds 1000 drivers and asserts nearest-first ordering and
 * the NX driver lock.
 */
@Tag("integration")
@Testcontainers
class DriverMatchingIntegrationTest {

    @Container
    static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    static StringRedisTemplate redis;
    static DriverMatchingService matching;

    @BeforeAll
    static void setup() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        matching = new DriverMatchingService(redis);
        seedDrivers();
    }

    @AfterAll
    static void teardown() {
        redis.getConnectionFactory().getConnection().close();
    }

    static void seedDrivers() {
        double baseLat = 12.9716;
        double baseLng = 77.5946;
        for (int i = 0; i < 1000; i++) {
            String driverId = "driver-" + i;
            // spread drivers eastward, increasing distance with index
            double lng = baseLng + (i * 0.0005);
            redis.opsForGeo().add("geo:drivers:active", new Point(lng, baseLat), driverId);
            redis.opsForHash().putAll("driver:state:" + driverId, Map.of(
                    "status", DriverStatus.AVAILABLE.name(),
                    "vehicleType", VehicleType.ECONOMY.name(),
                    "rating", "4.8",
                    "acceptanceRate", "0.92"));
        }
    }

    @Test
    void returnsNearestDriversFirst() {
        List<DriverMatchingService.RankedDriver> candidates =
                matching.findCandidates(12.9716, 77.5946, 5.0, VehicleType.ECONOMY);

        assertFalse(candidates.isEmpty(), "should find candidates");
        // closest seeded driver is driver-0 (same point)
        assertEquals("driver-0", candidates.get(0).candidate().driverId());

        // scores must be in non-increasing order (nearest/highest score first)
        for (int i = 1; i < candidates.size(); i++) {
            assertTrue(candidates.get(i - 1).score() >= candidates.get(i).score(),
                    "candidates must be ranked by score descending");
        }
        // and distances non-decreasing for equal-rating drivers
        for (int i = 1; i < candidates.size(); i++) {
            assertTrue(candidates.get(i - 1).candidate().distanceKm()
                            <= candidates.get(i).candidate().distanceKm() + 1e-6,
                    "nearest-first distance ordering");
        }
    }

    @Test
    void driverLockIsExclusive() {
        assertTrue(matching.lockDriver("driver-0", "trip-A"));
        assertFalse(matching.lockDriver("driver-0", "trip-B"), "second lock must fail (NX)");
        matching.unlockDriver("driver-0");
        assertTrue(matching.lockDriver("driver-0", "trip-C"), "lock available after release");
        matching.unlockDriver("driver-0");
    }

    @Test
    void respectsRadiusBoundary() {
        // a tiny radius should exclude far drivers but still include the co-located one
        List<DriverMatchingService.RankedDriver> candidates =
                matching.findCandidates(12.9716, 77.5946, 0.1, VehicleType.ECONOMY);
        assertFalse(candidates.isEmpty());
        candidates.forEach(c -> assertTrue(c.candidate().distanceKm() <= 0.1 + 1e-6));
    }
}
