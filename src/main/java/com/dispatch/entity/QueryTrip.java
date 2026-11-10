package com.dispatch.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "query_trips")
public class QueryTrip {

    @Id
    @Column(name = "trip_id")
    private String tripId;

    @Column(name = "rider_id")
    private String riderId;

    @Column(name = "driver_id")
    private String driverId;

    private String status;

    @Column(name = "pickup_lat")
    private double pickupLat;
    @Column(name = "pickup_lng")
    private double pickupLng;
    @Column(name = "drop_lat")
    private double dropLat;
    @Column(name = "drop_lng")
    private double dropLng;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "estimated_fare")
    private double estimatedFare;
    @Column(name = "final_fare")
    private double finalFare;
    @Column(name = "surge_multiplier")
    private double surgeMultiplier = 1.0;
    @Column(name = "distance_km")
    private double distanceKm;
    @Column(name = "duration_minutes")
    private double durationMinutes;

    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getRiderId() {
        return riderId;
    }

    public void setRiderId(String riderId) {
        this.riderId = riderId;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getPickupLat() {
        return pickupLat;
    }

    public void setPickupLat(double pickupLat) {
        this.pickupLat = pickupLat;
    }

    public double getPickupLng() {
        return pickupLng;
    }

    public void setPickupLng(double pickupLng) {
        this.pickupLng = pickupLng;
    }

    public double getDropLat() {
        return dropLat;
    }

    public void setDropLat(double dropLat) {
        this.dropLat = dropLat;
    }

    public double getDropLng() {
        return dropLng;
    }

    public void setDropLng(double dropLng) {
        this.dropLng = dropLng;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public double getEstimatedFare() {
        return estimatedFare;
    }

    public void setEstimatedFare(double estimatedFare) {
        this.estimatedFare = estimatedFare;
    }

    public double getFinalFare() {
        return finalFare;
    }

    public void setFinalFare(double finalFare) {
        this.finalFare = finalFare;
    }

    public double getSurgeMultiplier() {
        return surgeMultiplier;
    }

    public void setSurgeMultiplier(double surgeMultiplier) {
        this.surgeMultiplier = surgeMultiplier;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public double getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(double durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
