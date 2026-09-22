package com.learn.apigateway.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

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
    private final ObjectMapper objectMapper;

    public AuthController(CognitoAuthClient cognitoAuthClient, ObjectMapper objectMapper) {
        this.cognitoAuthClient = cognitoAuthClient;
        this.objectMapper = objectMapper;
    }

    public record LoginRequest(String email, String password) {
    }

    public record GoogleCallbackRequest(String code, String redirectUri, String codeVerifier) {
    }

    // email is null for password login/refresh -- the frontend already has the email
    // there (it's whatever the user just typed into the login form). Google sign-in never
    // types an email anywhere, so this is the one path that actually needs the Gateway to
    // hand it back, for AuthContext to run the same POST /users/me profile sync password
    // login already does -- without that, a Google user's app_user row would be stuck on
    // the auto-created "<sub>@cognito.local" placeholder forever (see UserController's own
    // comment on that placeholder, and the cleanup this project just did for a pile of
    // load-test accounts stuck in exactly that state).
    public record TokenResponse(String accessToken, long expiresIn, String email) {
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

    // Same shape as login()/refresh() above -- the frontend redirected the browser to
    // Cognito's Hosted UI, the user approved "Continue with Google" there, and Cognito
    // redirected back with a one-time authorization code plus whatever PKCE code_verifier
    // the frontend originally generated (see CognitoAuthClient.exchangeAuthorizationCode's
    // own comment on why that's needed). This is the server-side leg of that exchange.
    @PostMapping("/google/callback")
    public Mono<ResponseEntity<TokenResponse>> googleCallback(
            @RequestBody GoogleCallbackRequest request, ServerWebExchange exchange) {
        return cognitoAuthClient.exchangeAuthorizationCode(request.code(), request.redirectUri(), request.codeVerifier())
                .map(result -> {
                    setRefreshCookie(exchange, result.get("refresh_token").asText());
                    String email = decodeEmailFromIdToken(result.get("id_token").asText());
                    return ResponseEntity.ok(new TokenResponse(
                            result.get("access_token").asText(), result.get("expires_in").asLong(), email));
                })
                .onErrorResume(CognitoAuthException.class, ex -> Mono.just(errorResponse(ex)));
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
        return new TokenResponse(result.get("AccessToken").asText(), result.get("ExpiresIn").asLong(), null);
    }

    private ResponseEntity<TokenResponse> errorResponse(CognitoAuthException ex) {
        // NotAuthorizedException: the JSON API's (InitiateAuth/GlobalSignOut) error type
        // for bad credentials/an invalid refresh token. invalid_grant: the OAuth2 token
        // endpoint's equivalent for an expired, already-used, or otherwise invalid
        // authorization code -- both mean the same thing from this method's point of view
        // ("whatever you presented isn't valid, try logging in again"), so both map to 401
        // rather than a generic 400.
        boolean unauthorized = "NotAuthorizedException".equals(ex.getCognitoErrorType())
                || "invalid_grant".equals(ex.getCognitoErrorType());
        return ResponseEntity.status(unauthorized ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST).build();
    }

    // The id_token's payload, deliberately NOT re-verified here -- this method only ever
    // runs on a token this same request just received directly from Cognito over TLS (see
    // googleCallback above), never on anything a caller could supply themselves, so
    // there's nothing an attacker could forge here the way there would be if this method
    // took client-supplied input. Just needs the email Google/Cognito already put in it,
    // for the profile-sync call AuthContext makes afterward.
    private String decodeEmailFromIdToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            return payload.path("email").asText(null);
        } catch (Exception malformedToken) {
            return null;
        }
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
