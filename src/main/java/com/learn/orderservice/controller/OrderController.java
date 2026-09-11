package com.learn.orderservice.controller;

import com.learn.orderservice.dto.CreateOrderRequest;
import com.learn.orderservice.dto.OrderResponse;
import com.learn.orderservice.entity.*;
import com.learn.orderservice.exception.ForbiddenException;
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
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            // Set by the Gateway's UserIdentityHeaderFilter from the caller's own validated JWT --
            // this service never sees a raw token, just this one trusted header. Missing entirely
            // means something bypassed the Gateway or the filter broke, so failing loudly (a 400,
            // via Spring's default handling of a required header) is correct here, not a fallback.
            @RequestHeader("X-User-Sub") String cognitoSub
    ) {
        AppUser user = appUserRepository.findByCognitoSub(cognitoSub)
                .orElseGet(() -> createUserForCognitoSub(cognitoSub));

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

    // The first order ever placed by a given Cognito identity creates its app_user row on
    // the spot -- there's no separate signup step yet. email/name are placeholders: the
    // access token's "sub" claim carries no profile info (name, real email) at all, only
    // the ID token does, and we deliberately don't send that to APIs (see Phase 4 notes).
    // A real signup flow would populate these properly instead of synthesizing them.
    private AppUser createUserForCognitoSub(String cognitoSub) {
        AppUser user = new AppUser();
        user.setCognitoSub(cognitoSub);
        user.setEmail(cognitoSub + "@cognito.local");
        user.setName("Cognito User");
        return appUserRepository.save(user);
    }

    @GetMapping("/orders/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long id,
            @RequestHeader("X-User-Sub") String cognitoSub
    ) {
        Order order = orderRepository.findByIdWithUserAndItems(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        // Ownership check: this order existing isn't enough -- it has to be *yours*. Without
        // this, any authenticated caller could read any order just by guessing/incrementing
        // the id in the URL, regardless of who actually placed it.
        boolean isOwner = appUserRepository.findByCognitoSub(cognitoSub)
                .map(caller -> caller.getId().equals(order.getUser().getId()))
                .orElse(false);
        if (!isOwner) {
            throw new ForbiddenException("Order " + id + " does not belong to you");
        }

        return ResponseEntity.ok(OrderResponse.from(order));
    }

    // The history page's actual endpoint: identity comes from the same trusted header as
    // order creation, never from a path parameter a client could swap out to see someone
    // else's orders. (An earlier GET /users/{id}/orders took an arbitrary path id instead --
    // removed entirely once ownership checks made it purely redundant with this endpoint.)
    @GetMapping("/orders/mine")
    @Transactional(readOnly = true)
    public ResponseEntity<List<OrderResponse>> getMyOrders(@RequestHeader("X-User-Sub") String cognitoSub) {
        // A Cognito identity that has never placed an order has no app_user row at all yet
        // (see createUserForCognitoSub) -- that's a normal "no history", not an error.
        List<OrderResponse> responses = appUserRepository.findByCognitoSub(cognitoSub)
                .map(user -> orderRepository.findAllByUserIdWithUserAndItems(user.getId())
                        .stream()
                        .map(OrderResponse::from)
                        .toList())
                .orElse(List.of());
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
