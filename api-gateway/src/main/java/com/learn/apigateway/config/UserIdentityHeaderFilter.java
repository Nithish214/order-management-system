package com.learn.apigateway.config;

import org.springframework.core.Ordered;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

// This is the one place identity crosses from "a validated JWT" to plain trusted headers
// -- downstream services (Order Service, Inventory Service) never see a token or know
// Cognito exists at all; they just read these headers, exactly like they already trust
// that any request reaching them at all has already been authenticated here. This only
// holds because their ports stay off the public internet (the EC2 security group), same
// trust boundary as everything else since Phase 4.
//
// "sub" (not email) is used deliberately: it's on every Cognito token type, including
// the Access token this app actually sends to APIs -- email only lives on the ID token,
// which we correctly never send to APIs (see the Phase 4 write-up).
@Component
public class UserIdentityHeaderFilter implements GlobalFilter, Ordered {

    private static final String USER_SUB_HEADER = "X-User-Sub";
    // Lets Order Service redact admin-only fields (currently just SKU -- see
    // ProductController/ProductResponse) from responses a non-admin can still otherwise
    // read, like GET /products. This is a DIFFERENT job from SecurityConfig's
    // hasAuthority rules: those either let a whole request through or reject it outright
    // (all-or-nothing), where what's needed here is "let the request through, but change
    // what's IN the response" for a route every authenticated user is allowed to call.
    private static final String USER_IS_ADMIN_HEADER = "X-User-Is-Admin";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                // Same "admin" group check as jwtAuthenticationConverter in SecurityConfig --
                // deliberately not reusing that converter's ROLE_admin authority here, since
                // this filter runs on the plain JwtAuthenticationToken/Jwt, not on whatever
                // authorities got attached to it, and re-deriving it directly from the claim
                // keeps this filter usable regardless of how that converter is implemented.
                .map(authToken -> {
                    String sub = authToken.getToken().getClaimAsString("sub");
                    List<String> groups = authToken.getToken().getClaimAsStringList("cognito:groups");
                    boolean isAdmin = groups != null && groups.contains("admin");
                    return exchange.getRequest().mutate()
                            .header(USER_SUB_HEADER, sub)
                            .header(USER_IS_ADMIN_HEADER, String.valueOf(isAdmin))
                            .build();
                })
                // Unauthenticated requests (the OPTIONS preflight permitAll rule) have no
                // principal at all -- getPrincipal() completes empty, not with an error, so
                // this just forwards the request unchanged rather than failing it.
                .flatMap(mutatedRequest -> chain.filter(exchange.mutate().request(mutatedRequest).build()))
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        // Must run before the routing filter that actually proxies the request downstream
        // (registered at Ordered.LOWEST_PRECEDENCE) -- any value well below that qualifies.
        return -1;
    }
}
