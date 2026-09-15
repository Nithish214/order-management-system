package com.learn.apigateway.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

// The "BFF" (Backend-For-Frontend) half of login: the one and only place the refresh
// token Cognito issues ever exists outside Cognito itself. It never reaches the
// browser's JavaScript -- only this controller ever reads/writes it, held in an
// HttpOnly cookie the browser attaches automatically and no script can read. This
// replaces the old approach (frontend/src/auth/cognito.js calling Cognito directly),
// where the refresh token was thrown away entirely -- there was nowhere safe in a pure
// static frontend to keep it, so a page reload always logged the user out.
//
// These endpoints are unauthenticated by nature (see SecurityConfig's permitAll rule
// for them) -- login has no token yet, refresh only has the cookie, logout must still
// work even against an already-expired access token.
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final CognitoAuthClient cognitoAuthClient;

    public AuthController(CognitoAuthClient cognitoAuthClient) {
        this.cognitoAuthClient = cognitoAuthClient;
    }

    public record LoginRequest(String email, String password) {
    }

    public record TokenResponse(String accessToken, long expiresIn) {
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<TokenResponse>> login(@RequestBody LoginRequest request, ServerWebExchange exchange) {
        return cognitoAuthClient.login(request.email(), request.password())
                .map(result -> {
                    setRefreshCookie(exchange, result.get("RefreshToken").asText());
                    return ResponseEntity.ok(toTokenResponse(result));
                })
                .onErrorResume(CognitoAuthException.class, ex -> Mono.just(errorResponse(ex)));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<TokenResponse>> refresh(ServerWebExchange exchange) {
        String refreshToken = readRefreshCookie(exchange.getRequest());
        if (refreshToken == null) {
            // No cookie at all -- never logged in here, or it already expired/was cleared.
            // Not an error to log, just "there's no session to restore".
            return Mono.just(unauthorized());
        }

        return cognitoAuthClient.refresh(refreshToken)
                .map(result -> {
                    // Cognito doesn't rotate the refresh token by default (this app client has
                    // refresh-token rotation off) -- only reset the cookie if a new one actually
                    // came back, so this never overwrites a still-valid cookie with nothing.
                    if (result.hasNonNull("RefreshToken")) {
                        setRefreshCookie(exchange, result.get("RefreshToken").asText());
                    }
                    return ResponseEntity.ok(toTokenResponse(result));
                })
                .onErrorResume(CognitoAuthException.class, ex -> {
                    // The refresh token itself is dead (expired past its ~30-day life, or
                    // revoked -- see logout's GlobalSignOut below) -- clear the cookie so the
                    // browser stops sending a token that will only ever fail from here on.
                    clearRefreshCookie(exchange);
                    return Mono.just(unauthorized());
                });
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            ServerWebExchange exchange) {
        clearRefreshCookie(exchange);

        // Best-effort: also revoke the refresh token at Cognito itself, so a stolen/leaked
        // cookie can't be replayed after the user explicitly logged out. This never blocks
        // logout from the browser's point of view -- the cookie is already cleared above
        // regardless of whether this call to Cognito succeeds, and an already-expired
        // access token (the common case -- logout usually happens well after login) is
        // simply swallowed rather than turning a logout click into a visible error.
        Mono<Void> revoke = (authorization != null && authorization.startsWith("Bearer "))
                ? cognitoAuthClient.globalSignOut(authorization.substring(7)).onErrorResume(e -> Mono.empty())
                : Mono.empty();

        return revoke.then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    private TokenResponse toTokenResponse(JsonNode result) {
        return new TokenResponse(result.get("AccessToken").asText(), result.get("ExpiresIn").asLong());
    }

    private ResponseEntity<TokenResponse> errorResponse(CognitoAuthException ex) {
        HttpStatus status = "NotAuthorizedException".equals(ex.getCognitoErrorType())
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).build();
    }

    private ResponseEntity<TokenResponse> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private void setRefreshCookie(ServerWebExchange exchange, String refreshToken) {
        exchange.getResponse().addCookie(ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                // HttpOnly: invisible to JavaScript entirely -- this is the actual fix. An XSS
                // payload injected into the page can no longer read this the way it could read
                // a token sitting in localStorage.
                .httpOnly(true)
                // Secure: the browser will only ever send this over HTTPS -- true of both the
                // CloudFront frontend and the DuckDNS/Let's Encrypt Gateway, so no downside.
                .secure(true)
                // SameSite=None (paired with Secure, which it requires) because this genuinely
                // is a cross-site request from the browser's point of view: the frontend
                // (the CloudFront domain) and this Gateway (the DuckDNS domain) are different
                // registrable domains. Lax/Strict would silently never send this cookie back.
                .sameSite("None")
                // Scoped to /auth only -- the sole place this cookie is ever read (refresh,
                // logout) -- no reason for it to also ride along on every proxied /orders,
                // /stock, etc. request.
                .path("/auth")
                // Matches Cognito's default refresh-token validity (30 days) for this app
                // client, so the cookie doesn't outlive, or get discarded well before, the
                // token it's holding.
                .maxAge(Duration.ofDays(30))
                .build());
    }

    private void clearRefreshCookie(ServerWebExchange exchange) {
        exchange.getResponse().addCookie(ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/auth")
                .maxAge(Duration.ZERO)
                .build());
    }

    private String readRefreshCookie(ServerHttpRequest request) {
        var cookie = request.getCookies().getFirst(REFRESH_COOKIE_NAME);
        return cookie != null ? cookie.getValue() : null;
    }
}
