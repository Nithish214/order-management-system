package com.learn.inventoryservice.repository;

import com.learn.inventoryservice.entity.OrderReservationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderReservationItemRepository extends JpaRepository<OrderReservationItem, Long> {
    List<OrderReservationItem> findByOrderId(Long orderId);
    void deleteByOrderId(Long orderId);
}
