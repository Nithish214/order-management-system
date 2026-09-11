package com.learn.apigateway.config;

import org.springframework.core.Ordered;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// This is the one place identity crosses from "a validated JWT" to "a plain trusted
// header" -- downstream services (Order Service, Inventory Service) never see a token
// or know Cognito exists at all; they just read X-User-Sub, exactly like they already
// trust that any request reaching them at all has already been authenticated here. This
// only holds because their ports stay off the public internet (the EC2 security group),
// same trust boundary as everything else since Phase 4.
//
// "sub" (not email) is used deliberately: it's on every Cognito token type, including
// the Access token this app actually sends to APIs -- email only lives on the ID token,
// which we correctly never send to APIs (see the Phase 4 write-up).
@Component
public class UserIdentityHeaderFilter implements GlobalFilter, Ordered {

    private static final String USER_SUB_HEADER = "X-User-Sub";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .map(authToken -> authToken.getToken().getClaimAsString("sub"))
                // Unauthenticated requests (the OPTIONS preflight permitAll rule) have no
                // principal at all -- getPrincipal() completes empty, not with an error, so
                // this just forwards the request unchanged rather than failing it.
                .flatMap(sub -> {
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header(USER_SUB_HEADER, sub)
                            .build();
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        // Must run before the routing filter that actually proxies the request downstream
        // (registered at Ordered.LOWEST_PRECEDENCE) -- any value well below that qualifies.
        return -1;
    }
}
