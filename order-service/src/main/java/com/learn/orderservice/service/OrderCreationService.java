package com.learn.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.orderservice.dto.CreateOrderRequest;
import com.learn.orderservice.dto.OrderResponse;
import com.learn.orderservice.entity.*;
import com.learn.orderservice.event.OutboxEventCreated;
import com.learn.orderservice.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// One full attempt at creating an order for a given Idempotency-Key -- pulled out of
// OrderController and given its own @Transactional method for the same reason
// StockReservationService exists in Inventory Service: OrderController needs to call this
// FRESH (through this bean's own Spring proxy) so that if the transaction below rolls back
// (see attemptCreateOrder's comment on the unique-constraint race), the caller gets a clean
// exception to catch and react to -- calling an @Transactional method on `this` from within
// the same class would bypass the proxy and silently skip the rollback-on-exception behavior
// this whole design depends on.
@Service
public class OrderCreationService {

    private final OrderRepository orderRepository;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    public OrderCreationService(
            OrderRepository orderRepository,
            AppUserRepository appUserRepository,
            ProductRepository productRepository,
            ShippingAddressRepository shippingAddressRepository,
            OutboxEventRepository outboxEventRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.appUserRepository = appUserRepository;
        this.productRepository = productRepository;
        this.shippingAddressRepository = shippingAddressRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderCreationResult attemptCreateOrder(CreateOrderRequest request, String cognitoSub, String idempotencyKey) {
        AppUser user = appUserRepository.findOrCreate(cognitoSub);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // Looked up by id AND owner in one query -- an address that exists but belongs to someone
        // else is indistinguishable from one that does not exist, so this can never silently ship
        // to a stranger's address, and cannot be used to probe which ids exist. Checked before
        // any item work so a bad address fails fast, as a plain 404 with a clear message, and
        // nothing is written. A brand-new account (just created above) has no addresses at all,
        // so it correctly fails here too.
        ShippingAddress shippingAddress = shippingAddressRepository
                .findByIdAndUserId(request.getAddressId(), user.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Shipping address not found -- choose one of your saved addresses"));

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

        // Status starts PENDING -- this service doesn't know yet whether Inventory Service can
        // actually fulfill it; InventoryOutcomeListener flips it to CONFIRMED/CANCELLED later.
        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING);
        // COPIED onto the order, not linked: same principle as unitPrice above. Edit or delete
        // this saved address tomorrow and this order still records where it was really sent.
        order.setShippingAddress(shippingAddress.toSnapshot());

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
        // Whatever CorrelationIdFilter put in MDC for this request -- captured here,
        // once, rather than read again later at publish time, since publish can happen
        // on a different thread or minutes later via the poller (see this field's own
        // comment on the entity for the full reasoning).
        outboxEvent.setCorrelationId(MDC.get(com.learn.orderservice.config.CorrelationIdFilter.MDC_KEY));
        outboxEvent = outboxEventRepository.save(outboxEvent);
        outboxEvent.setPayload(buildOrderCreatedPayload(savedOrder, outboxEvent.getId()));

        int status = HttpStatus.CREATED.value();
        String responseBody = serialize(OrderResponse.from(savedOrder));

        // The row that makes this whole feature actually work: `key` is its PRIMARY KEY, so
        // if some other request with this exact Idempotency-Key already committed between
        // OrderController's initial lookup and this INSERT (the genuine-race case -- a
        // double-click firing two nearly-simultaneous requests), the database itself rejects
        // this INSERT with a unique-constraint violation. Spring translates that into
        // DataIntegrityViolationException, which -- being an unchecked exception -- rolls
        // back this ENTIRE transaction: the order and outbox event built above are undone
        // right along with it, not left behind as an orphaned duplicate. OrderController
        // catches that exception specifically and replays the winner's stored response
        // instead of surfacing a raw 500 or silently creating a second order.
        idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKey, savedOrder.getId(), status, responseBody));

        // publishEvent() itself is synchronous and returns immediately -- it does NOT block
        // waiting for this transaction to commit, and does not itself talk to Kafka. All it
        // does here is register OutboxEventCreatedListener's callback with Spring's
        // transaction synchronization machinery; that callback is what's deferred until
        // AFTER_COMMIT; this line of code runs and returns right away, well before that.
        applicationEventPublisher.publishEvent(new OutboxEventCreated(outboxEvent.getId()));

        return new OrderCreationResult(status, responseBody);
    }

    private String serialize(OrderResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            // OrderResponse is a plain DTO of primitives/nested DTOs -- there's no realistic
            // way for Jackson to fail serializing it. Wrapped as unchecked so a genuinely
            // impossible failure still rolls back this transaction like anything else in
            // here, rather than forcing this method to declare a checked exception for a
            // case that, in practice, cannot happen.
            throw new IllegalStateException("Could not serialize order response", e);
        }
    }

    // Deliberately minimal -- just eventId + orderId + items, no rejection info yet. Mirrors
    // OrderController's own copy of this shape (see its buildOrderCancelledPayload) for the
    // OrderCancelled event.
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

    // status/body only -- OrderController re-wraps this as a ResponseEntity<String> with
    // the body written verbatim (see its replay() helper), rather than this returning a
    // ResponseEntity itself, since a plain @Service class has no business constructing
    // HTTP-layer types.
    public record OrderCreationResult(int status, String body) {
    }
}
