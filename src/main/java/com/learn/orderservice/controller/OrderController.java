package com.learn.orderservice.controller;

import com.learn.orderservice.dto.CreateOrderRequest;
import com.learn.orderservice.dto.OrderResponse;
import com.learn.orderservice.entity.*;
import com.learn.orderservice.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/")
public class OrderController {

    private final OrderRepository orderRepository;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final OutboxEventRepository outboxEventRepository;

    public OrderController(
            OrderRepository orderRepository,
            AppUserRepository appUserRepository,
            ProductRepository productRepository,
            OutboxEventRepository outboxEventRepository
    ) {
        this.orderRepository = orderRepository;
        this.appUserRepository = appUserRepository;
        this.productRepository = productRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        AppUser user = appUserRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + request.getUserId()));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            // Only used for price/existence here -- Inventory Service is the source of truth for
            // stock now (see InventoryOutcomeListener), so this service no longer tracks stock at all.
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found: " + itemRequest.getProductId()));

            BigDecimal lineTotal = product.getUnitPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            totalAmount = totalAmount.add(lineTotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setUnitPrice(product.getUnitPrice());
            orderItem.setLineTotal(lineTotal);
            orderItems.add(orderItem);
        }

        // Transactional boundary matters: this writes order, order items, and outbox event together.
        // Status starts PENDING -- this service doesn't know yet whether Inventory Service can
        // actually fulfill it; InventoryOutcomeListener flips it to CONFIRMED/CANCELLED later.
        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING);

        for (OrderItem orderItem : orderItems) {
            orderItem.setOrder(order);
            order.getItems().add(orderItem);
        }

        Order savedOrder = orderRepository.save(order);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("Order");
        outboxEvent.setAggregateId(String.valueOf(savedOrder.getId()));
        outboxEvent.setEventType("OrderCreated");
        outboxEvent.setStatus(OutboxStatus.PENDING);
        // Placeholder: the payload column is NOT NULL, and we need this row's generated id
        // (allocated by save()) before we can embed it in its own payload as the idempotency
        // key consumers will use. Overwritten below once that id exists.
        outboxEvent.setPayload("{}");
        outboxEvent = outboxEventRepository.save(outboxEvent);
        outboxEvent.setPayload(buildOrderCreatedPayload(savedOrder, outboxEvent.getId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(savedOrder));
    }

    @GetMapping("/orders/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        Order order = orderRepository.findByIdWithUserAndItems(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/users/{id}/orders")
    @Transactional(readOnly = true)
    public ResponseEntity<List<OrderResponse>> getUserOrders(@PathVariable("id") Long userId) {
        appUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        List<OrderResponse> responses = orderRepository.findAllByUserIdWithUserAndItems(userId)
                .stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    private String buildOrderCreatedPayload(Order order, Long eventId) {
        StringBuilder payload = new StringBuilder();
        payload.append("{\n");
        payload.append("  \"eventId\": ").append(eventId).append(",\n");
        payload.append("  \"orderId\": ").append(order.getId()).append(",\n");
        payload.append("  \"userId\": ").append(order.getUser().getId()).append(",\n");
        payload.append("  \"status\": \"").append(order.getStatus()).append("\",\n");
        payload.append("  \"totalAmount\": \"").append(order.getTotalAmount()).append("\",\n");
        payload.append("  \"items\": [\n");

        for (int i = 0; i < order.getItems().size(); i++) {
            OrderItem item = order.getItems().get(i);
            payload.append("    {\n");
            payload.append("      \"productId\": ").append(item.getProduct().getId()).append(",\n");
            payload.append("      \"quantity\": ").append(item.getQuantity()).append(",\n");
            payload.append("      \"unitPrice\": \"").append(item.getUnitPrice()).append("\"\n");
            payload.append("    }");
            if (i < order.getItems().size() - 1) {
                payload.append(",");
            }
            payload.append("\n");
        }

        payload.append("  ]\n");
        payload.append("}\n");
        return payload.toString();
    }
}
