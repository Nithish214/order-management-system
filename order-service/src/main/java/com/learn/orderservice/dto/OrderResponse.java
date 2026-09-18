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
        // Read live from the current Product row, not snapshotted onto OrderItem the way
        // unitPrice/lineTotal deliberately are (see OrderCreationService) -- a renamed
        // product would show its new name on old orders, unlike price, which is meant to
        // freeze at whatever it cost when you actually bought it. Acceptable here: this
        // catalog's names don't change after being seeded, and "what did I order" is a
        // much weaker promise than "what did I pay." Free to add besides -- both
        // order-fetching queries already `join fetch i.product`, so this is zero extra
        // queries, not a new N+1.
        private String productName;
        // Same "first image is the thumbnail" convention as everywhere else this app
        // shows one image for a product (the grid card, the cart) -- Product.images is
        // already @OrderBy("id ASC"), so images.get(0) is that thumbnail. Null (not an
        // empty string) when the product has no image at all, matching ProductResponse's
        // own images list being empty in that case -- the frontend already knows how to
        // render "no image yet" as a plain placeholder rather than a broken-image icon.
        // Free to add despite Product.images being LAZY: it's @BatchSize(size = 100) on
        // the entity itself (see Product.java), so Hibernate batches this across every
        // order item's product in one extra query, not one query per item.
        private String imageUrl;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;

        public static OrderItemResponse from(com.learn.orderservice.entity.OrderItem item) {
            OrderItemResponse dto = new OrderItemResponse();
            dto.setProductId(item.getProduct().getId());
            dto.setProductName(item.getProduct().getName());
            var images = item.getProduct().getImages();
            dto.setImageUrl(images.isEmpty() ? null : images.get(0).getImageUrl());
            dto.setQuantity(item.getQuantity());
            dto.setUnitPrice(item.getUnitPrice());
            dto.setLineTotal(item.getLineTotal());
            return dto;
        }
    }
}
