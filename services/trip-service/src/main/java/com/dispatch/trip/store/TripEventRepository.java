package com.dispatch.trip.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripEventRepository extends JpaRepository<TripEventEntity, Long> {

    List<TripEventEntity> findByTripIdOrderBySequenceNumberAsc(String tripId);

    long countByTripId(String tripId);
}
