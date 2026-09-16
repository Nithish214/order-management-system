package com.learn.apigateway.config;

import org.springframework.core.Ordered;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

// This is where one order's whole journey -- api-gateway -> order-service -> Kafka ->
// inventory-service -> Kafka -> order-service -> Kafka -> payment-service -> Kafka ->
// order-service -- gets a single shared id stamped across every hop, purely so
// `grep <id>` across all four services' logs shows the complete story of one order
// instead of manually reading each service's logs separately and lining up timestamps by
// hand. Every downstream service reads this same header and puts it in its own MDC (see
// each service's own CorrelationIdFilter/listener changes) so it shows up on every log
// line for that request/message, and order-service/payment-service additionally persist
// it on their outbox_event rows so it survives the async gap between "HTTP request wrote
// this row" and "some other thread/the scheduled poller actually published it to Kafka".
//
// Same GlobalFilter/Ordered shape as UserIdentityHeaderFilter -- see that class for why
// this pattern (mutate the request, pass the mutated exchange down the chain) is how
// Spring Cloud Gateway's reactive filter chain adds a header before proxying downstream.
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Reuse an incoming id if a caller already sent one (a future server-to-server
        // integration, or a retried client request that wants to tie its own retry
        // together under one id) -- generate a fresh one otherwise. Either way, every
        // request that reaches a backend service carries exactly one of these.
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        // Echoed back on the response too -- lets a caller (or whoever's looking at
        // browser DevTools) see exactly which id to go grep for in the logs, without
        // needing access to the request they just made.
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // Same reasoning as UserIdentityHeaderFilter: must run before the routing filter
        // that actually proxies downstream (Ordered.LOWEST_PRECEDENCE). No ordering
        // dependency between the two of them, since each only adds its own header.
        return -1;
    }
}
