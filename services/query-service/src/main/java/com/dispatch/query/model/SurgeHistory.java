package com.dispatch.query.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "surge_history")
public class SurgeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String geohash;
    private double multiplier;

    @Column(name = "demand_count")
    private long demandCount;
    @Column(name = "supply_count")
    private long supplyCount;
    @Column(name = "recorded_at")
    private Instant recordedAt;

    protected SurgeHistory() {
    }

    public SurgeHistory(String geohash, double multiplier, long demandCount, long supplyCount, Instant recordedAt) {
        this.geohash = geohash;
        this.multiplier = multiplier;
        this.demandCount = demandCount;
        this.supplyCount = supplyCount;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public String getGeohash() {
        return geohash;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public long getDemandCount() {
        return demandCount;
    }

    public long getSupplyCount() {
        return supplyCount;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
