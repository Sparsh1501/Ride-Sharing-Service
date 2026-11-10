package com.dispatch.repository;

import com.dispatch.entity.TripEventEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripEventRepository extends JpaRepository<TripEventEntity, Long> {

    List<TripEventEntity> findByTripIdOrderBySequenceNumberAsc(String tripId);

    long countByTripId(String tripId);
}
