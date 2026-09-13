package com.learn.orderservice.repository;

import com.learn.orderservice.entity.OutboxEvent;
import com.learn.orderservice.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Pageable caps how many rows come back in one call, so the poller processes a bounded
    // batch each run instead of loading the whole table -- Spring Data returns just the
    // content as a List (rather than a Page) because the method's return type says so.
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
