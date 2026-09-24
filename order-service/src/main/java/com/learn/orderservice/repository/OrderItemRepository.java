package com.learn.orderservice.repository;

import com.learn.orderservice.entity.OrderItem;
import com.learn.orderservice.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

// Order.items already covers "the items on one specific, already-loaded order" -- the one
// thing that collection can't answer is "across every order this user has ever placed, did
// any of them contain this product," which is exactly what review-eligibility needs (see
// ReviewController). That's a query spanning many orders, not a single order's own
// collection, hence a repository of its own rather than reusing Order's existing mapping.
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // Deliberately checks order.status = CONFIRMED, not just "any order exists" -- a
    // PENDING order might still get rejected by inventory or payment, and a REJECTED/
    // CANCELLED one was never actually fulfilled. Only a CONFIRMED order (see
    // PaymentOutcomeListener) represents a real, completed purchase -- the only thing that
    // should ever grant review eligibility.
    boolean existsByProduct_IdAndOrder_UserIdAndOrder_Status(Long productId, Long userId, OrderStatus status);
}
