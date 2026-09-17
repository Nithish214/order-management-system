package com.learn.apigateway.config;

import org.springframework.core.Ordered;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

// Protects order-service/Kafka from being overwhelmed by a sudden burst of order
// creation -- exactly what caused the multi-hour Kafka backlog after the 2000-VU load
// test on 2026-09-16 (see the project memory notes for that incident). Deliberately a
// GLOBAL cap shared by every caller, not a per-user limit: the incident wasn't one
// abusive user, it was thousands of different simulated users each sending a perfectly
// reasonable request rate on their own -- only the combined total overwhelmed downstream
// capacity.
//
// This started as Spring Cloud Gateway's built-in RequestRateLimiter (backed by its
// RedisRateLimiter, which runs a Lua token-bucket script in Redis) -- abandoned after it
// was found, live, to silently fail open on every single request
// (RedisRateLimiter logging `Response{allowed=true, ..., tokensRemaining=-1}` -- its own
// documented sentinel for "the Redis call itself errored" -- with the underlying
// exception never actually surfacing in logs despite raising Spring Cloud Gateway's
// logging to TRACE). Rather than keep debugging a black-box library script, this does
// the same job with a handful of lines against Redis directly: a plain fixed-window
// counter, one INCR per second-long window, easy to reason about and verify by hand.
//
// A fixed window isn't as smooth as a real token bucket (in principle, a burst right at
// a window boundary could momentarily allow close to 2x the per-second limit -- half the
// old window's remaining allowance plus a fresh new window opening immediately after).
// That imprecision is an acceptable tradeoff here: the goal is "never let sustained
// demand vastly exceed what downstream services can absorb," not perfectly smooth
// pacing, and a fixed window is transparent enough to actually trust and debug.
@Component
public class OrderCreationRateLimiterFilter implements GlobalFilter, Ordered {

    // Grounded in this project's own load-test evidence, not guessed: the clean run on
    // this exact t3.small instance sustained 82 req/s with 0% errors; sustained failures
    // began once demand crossed roughly 100 req/s. 50/sec leaves real headroom under the
    // proven-safe ceiling. Revisit if the instance is ever resized -- more CPU raises the
    // real ceiling this number should track.
    private static final int LIMIT_PER_SECOND = 50;
    private static final String KEY_PREFIX = "order-rate-limit:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public OrderCreationRateLimiterFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // Path=/orders + Method=POST only -- reads (GET /orders/mine, GET /orders/{id})
        // and cancellations never fed the incident this exists to prevent; only the
        // volume of brand NEW orders did.
        boolean isOrderCreation = request.getMethod() == HttpMethod.POST
                && "/orders".equals(request.getPath().value());
        if (!isOrderCreation) {
            return chain.filter(exchange);
        }

        // One key per second-long window, e.g. "order-rate-limit:1789628044" -- INCR
        // creates it at 1 if absent (atomic, safe under real concurrency: Redis processes
        // commands from all callers one at a time, so two requests racing on the same
        // brand-new window can never both "win" the count==1 check below). The 2-second
        // expiry is just housekeeping so old windows' keys don't accumulate forever --
        // it's not what enforces the limit, the count check does that.
        long currentWindow = Instant.now().getEpochSecond();
        String key = KEY_PREFIX + currentWindow;

        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    Mono<Long> withExpirySet = count == 1
                            ? redisTemplate.expire(key, Duration.ofSeconds(2)).thenReturn(count)
                            : Mono.just(count);
                    return withExpirySet;
                })
                .flatMap(count -> {
                    if (count > LIMIT_PER_SECOND) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                })
                // Redis being briefly unreachable shouldn't take down order creation
                // entirely -- fail OPEN (let the request through) rather than closed,
                // same reasoning as Spring's own RedisRateLimiter defaults to. A rate
                // limiter that's temporarily not enforcing is a smaller problem than one
                // that turns a Redis blip into a full outage.
                .onErrorResume(ex -> chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        // After the id-stamping filters (-2, -1) have already run, but well before
        // routing -- doesn't need to be first, just needs to run before the request is
        // actually proxied downstream.
        return 0;
    }
}
