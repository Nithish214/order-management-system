package com.learn.orderservice.controller;

import com.learn.orderservice.dto.CreateOrderRequest;
import com.learn.orderservice.dto.OrderResponse;
import com.learn.orderservice.entity.*;
import com.learn.orderservice.event.OutboxEventCreated;
import com.learn.orderservice.event.OrderStatusChanged;
import com.learn.orderservice.exception.ForbiddenException;
import com.learn.orderservice.exception.InvalidOrderStateException;
import com.learn.orderservice.repository.*;
import com.learn.orderservice.service.OrderCreationService;
import com.learn.orderservice.service.OrderCreationService.OrderCreationResult;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderRepository orderRepository;
    private final AppUserRepository appUserRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OrderCreationService orderCreationService;

    public OrderController(
            OrderRepository orderRepository,
            AppUserRepository appUserRepository,
            OutboxEventRepository outboxEventRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            ApplicationEventPublisher applicationEventPublisher,
            OrderCreationService orderCreationService
    ) {
        this.orderRepository = orderRepository;
        this.appUserRepository = appUserRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.orderCreationService = orderCreationService;
    }

    // Idempotency-Key is REQUIRED, not optional-with-a-silent-fallback: an optional header
    // protects exactly the callers who remember to send it, and leaves unprotected exactly
    // the ones most likely to forget -- a hastily written script, a future integration nobody
    // thought to update. For a money-adjacent write like "place an order," silently degrading
    // to unsafe behavior for whoever skips the header is the wrong default; Spring itself
    // 400s this request before this method body even runs if the header is missing (see
    // GlobalExceptionHandler's MissingRequestHeaderException case for the response shape).
    //
    // Deliberately NOT @Transactional itself any more -- see OrderCreationService for where
    // the actual transactional work now lives and why. This method's only remaining job is:
    // check whether this exact key has already succeeded (fast path, no writes at all), and
    // if two requests for the same key are racing right now, catch the loser's constraint
    // violation and hand it the winner's response instead of a raw 500.
    @PostMapping("/orders")
    public ResponseEntity<String> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            // Set by the Gateway's UserIdentityHeaderFilter from the caller's own validated JWT --
            // this service never sees a raw token, just this one trusted header. Missing entirely
            // means something bypassed the Gateway or the filter broke, so failing loudly (a 400,
            // via Spring's default handling of a required header) is correct here, not a fallback.
            @RequestHeader("X-User-Sub") String cognitoSub,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            // Same trusted header ProductController already reads to redact admin-only fields --
            // reused here for the opposite shape of check: not "hide some data," but "refuse the
            // whole request." An admin account manages the catalog/stock, it isn't a customer, so
            // it placing real orders would pollute order history and stock reservations with test
            // noise. defaultValue "false" only matters if this header is ever missing entirely
            // (shouldn't happen behind the Gateway -- see the comment above), and false is the
            // safe side to default to: it lets the request proceed to the real checks below rather
            // than locking out a genuine customer because of a missing header.
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        if (isAdmin) {
            throw new ForbiddenException("Admin accounts cannot place orders");
        }

        // Fast path: this exact key has already resulted in an order (a plain retry, sent
        // sequentially after the first one already finished -- the common case, not the race
        // below). No writes at all here, just a lookup, so a client that retries constantly
        // for no reason costs almost nothing.
        var existing = idempotencyKeyRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get());
        }

        try {
            OrderCreationResult result = orderCreationService.attemptCreateOrder(request, cognitoSub, idempotencyKey);
            return ResponseEntity.status(result.status())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result.body());
        } catch (DataIntegrityViolationException ex) {
            // The genuine race: another request with this exact key committed between the
            // lookup above and attemptCreateOrder's own INSERT into idempotency_key -- e.g.
            // a double-click firing two requests within milliseconds of each other. Postgres/
            // Oracle both hold a lock on the unique index entry during an INSERT, so the
            // *losing* transaction's INSERT actually blocks until the winner commits or rolls
            // back -- meaning by the time this catch block runs, the winner's row is
            // guaranteed to already be committed and visible. Re-reading it here is therefore
            // safe, not a guess: it will be there.
            log.info("Idempotency-Key {} lost a concurrent create race -- replaying the winner's response",
                    idempotencyKey);
            return idempotencyKeyRepository.findById(idempotencyKey)
                    .map(this::replay)
                    // Should be unreachable given the guarantee above; if it somehow isn't
                    // there, this really was some other constraint violation, so surface the
                    // original error rather than hiding it behind a confusing NoSuchElement.
                    .orElseThrow(() -> ex);
        }
    }

    // Byte-for-byte replay of whatever was stored the first time this key succeeded -- not
    // OrderResponse.from(order) run again, which could theoretically produce different JSON
    // if the DTO's shape changes between the original call and a much later retry.
    private ResponseEntity<String> replay(IdempotencyKey stored) {
        return ResponseEntity.status(stored.getResponseStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(stored.getResponseBody());
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

    // Customer-initiated cancellation. Sets the order's status immediately (optimistically,
    // same reasoning as createOrder setting PENDING immediately) -- the customer doesn't
    // need to wait for Inventory Service to actually finish releasing stock before seeing
    // their order marked cancelled, since from their side the outcome isn't in question the
    // way order confirmation was. Inventory Service releases any reserved stock
    // asynchronously via the OrderCancelled event below; see its OrderCancelledListener.
    @PostMapping("/orders/{id}/cancel")
    @Transactional
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @RequestHeader("X-User-Sub") String cognitoSub
    ) {
        Order order = orderRepository.findByIdWithUserAndItems(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        boolean isOwner = appUserRepository.findByCognitoSub(cognitoSub)
                .map(caller -> caller.getId().equals(order.getUser().getId()))
                .orElse(false);
        if (!isOwner) {
            throw new ForbiddenException("Order " + id + " does not belong to you");
        }

        // Only PENDING and CONFIRMED are cancellable -- everything else is already terminal.
        // CONFIRMED is included (not just PENDING) because a customer should be able to
        // cancel an order that's already been confirmed but, say, hasn't shipped yet; that's
        // exactly the case where Inventory Service actually has stock reserved that needs
        // releasing, which is why this event exists instead of just flipping a status flag.
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(
                    "Order " + id + " cannot be cancelled from status " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("Order");
        outboxEvent.setAggregateId(String.valueOf(order.getId()));
        outboxEvent.setEventType("OrderCancelled");
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setPayload("{}");
        outboxEvent.setCorrelationId(org.slf4j.MDC.get(com.learn.orderservice.config.CorrelationIdFilter.MDC_KEY));
        outboxEvent = outboxEventRepository.save(outboxEvent);
        outboxEvent.setPayload(buildOrderCancelledPayload(order, outboxEvent.getId()));

        // Same event-driven publish as createOrder -- cancellation writes an outbox row
        // through the exact same mechanism, so it gets the exact same fast path.
        applicationEventPublisher.publishEvent(new OutboxEventCreated(outboxEvent.getId()));
        // Same AFTER_COMMIT-deferred email notification as OrderStatusUpdater's own
        // CONFIRMED/REJECTED outcomes -- see OrderStatusEmailListener. This is the one
        // status transition OrderStatusUpdater itself never sees (cancellation is
        // customer-initiated, handled entirely here), so it needs its own publish.
        applicationEventPublisher.publishEvent(new OrderStatusChanged(order.getId()));

        return ResponseEntity.ok(OrderResponse.from(order));
    }

    // Deliberately minimal -- just eventId + orderId, no item list. Inventory Service looks
    // up what it actually reserved for this order itself (see its OrderReservationItem
    // table) rather than trusting a repeated item list here; that's the more correct source
    // of truth, since it's Inventory's own bookkeeping of what it decremented, not Order
    // Service's belief about what should have been reserved.
    private String buildOrderCancelledPayload(Order order, Long eventId) {
        return """
                {
                  "eventId": %d,
                  "orderId": %d
                }
                """.formatted(eventId, order.getId());
    }
}
