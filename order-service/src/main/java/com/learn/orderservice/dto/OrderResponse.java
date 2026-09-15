package com.learn.orderservice.dto;

import com.learn.orderservice.entity.Order;
import com.learn.orderservice.entity.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class OrderResponse {

    private Long id;
    private Long userId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    // Only ever populated when status is REJECTED -- null for every other status.
    private String rejectionReason;
    private List<OrderItemResponse> items;

    public static OrderResponse from(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setUserId(order.getUser().getId());
        response.setStatus(order.getStatus());
        response.setTotalAmount(order.getTotalAmount());
        response.setCreatedAt(order.getCreatedAt());
        response.setRejectionReason(order.getRejectionReason());
        response.setItems(order.getItems().stream().map(OrderItemResponse::from).toList());
        return response;
    }

    @Getter
    @Setter
    public static class OrderItemResponse {
        private Long productId;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;

        public static OrderItemResponse from(com.learn.orderservice.entity.OrderItem item) {
            OrderItemResponse dto = new OrderItemResponse();
            dto.setProductId(item.getProduct().getId());
            dto.setQuantity(item.getQuantity());
            dto.setUnitPrice(item.getUnitPrice());
            dto.setLineTotal(item.getLineTotal());
            return dto;
        }
    }
}
