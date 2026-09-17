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
// POST-INCIDENT FIX (2026-09-17): the first version of this filter caused a real, live
// bug -- some responses were arriving at the browser cut off mid-stream
// (net::ERR_INCOMPLETE_CHUNKED_ENCODING), confirmed by disabling this exact filter and
// watching the symptom disappear entirely. Two things were wrong, both fixed below:
//
//   1. getOrder() returned -1, the EXACT SAME value as the pre-existing
//      UserIdentityHeaderFilter -- an accidental tie, not a deliberate choice. Two
//      Spring Cloud Gateway GlobalFilters sharing one order value have no guaranteed
//      relative execution order; which one actually ran first was left to chance
//      (bean registration order), not something either filter's code could rely on.
//
//   2. The original response header was added immediately/eagerly
//      (exchange.getResponse().getHeaders().add(...)), before chain.filter() even ran --
//      racing against however the gateway's own downstream routing filter (which
//      actually streams the backend's response back) manages that same response object.
//      Most of the time this composed fine; under the specific request pattern a real
//      browser produces (several requests fired in quick succession right after a page
//      load), it didn't.
//
// The fix: an explicit order distinct from UserIdentityHeaderFilter's, and using
// beforeCommit() -- WebFlux's own purpose-built hook for "run this right before the
// response is actually sent, no matter what else is happening to it" -- instead of an
// eager, racy mutation.
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
        // Effectively-final copy for use inside the lambda below -- correlationId itself
        // gets reassigned above, which the beforeCommit callback (a separate closure,
        // possibly invoked well after this method returns) can't capture directly.
        final String finalCorrelationId = correlationId;

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        // Echoed back on the response too -- lets a caller (or whoever's looking at
        // browser DevTools) see exactly which id to go grep for in the logs, without
        // needing access to the request they just made. beforeCommit's callback is
        // guaranteed by WebFlux to run exactly once, right before the response headers
        // actually get flushed to the client -- not "probably early enough," a real
        // guarantee, which is what an eager .add() call never had.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);
            return Mono.empty();
        });

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // Deliberately one step before UserIdentityHeaderFilter's -1, not an accidental
        // tie with it -- this filter doesn't depend on authentication state at all, so
        // there's no reason for its relative position to be left to chance the way it
        // was before.
        return -2;
    }
}
