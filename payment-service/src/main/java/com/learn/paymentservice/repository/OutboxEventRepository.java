package com.learn.paymentservice.repository;

import com.learn.paymentservice.entity.OutboxEvent;
import com.learn.paymentservice.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Pageable caps how many rows come back in one call, so OutboxPoller processes a
    // bounded batch each run instead of loading the whole table. Identical to Order
    // Service's own OutboxEventRepository.
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
