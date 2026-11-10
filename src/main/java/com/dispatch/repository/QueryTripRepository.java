package com.dispatch.repository;

import com.dispatch.entity.QueryTrip;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueryTripRepository extends JpaRepository<QueryTrip, String> {

    Page<QueryTrip> findByRiderIdOrderByCreatedAtDesc(String riderId, Pageable pageable);

    Page<QueryTrip> findByDriverIdOrderByCreatedAtDesc(String driverId, Pageable pageable);

    List<QueryTrip> findByDriverId(String driverId);

    long countByStatus(String status);
}
