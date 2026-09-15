package com.learn.apigateway.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

// Server-side counterpart to frontend/src/auth/cognito.js's callCognito() -- same public
// Cognito JSON API (no admin credentials, no client secret -- this app client has none),
// just called from the Gateway now instead of the browser. That move is the entire point
// of this class: login and refresh both need to set/read the httpOnly refresh-token
// cookie, and only a server can do that -- see AuthController.
//
// WebClient (not RestTemplate) because Spring Cloud Gateway already runs on the reactive
// WebFlux stack (see SecurityConfig's comments) -- everything here returns Mono so it
// composes into that same non-blocking pipeline instead of parking a thread per request.
@Component
public class CognitoAuthClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String clientId;

    public CognitoAuthClient(
            @Value("${cognito.region}") String region,
            @Value("${cognito.client-id}") String clientId,
            ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl("https://cognito-idp." + region + ".amazonaws.com/")
                .build();
    }

    public Mono<JsonNode> login(String email, String password) {
        return call("InitiateAuth", Map.of(
                "AuthFlow", "USER_PASSWORD_AUTH",
                "ClientId", clientId,
                "AuthParameters", Map.of("USERNAME", email, "PASSWORD", password)
        )).map(response -> response.get("AuthenticationResult"));
    }

    // Cognito's REFRESH_TOKEN_AUTH flow: exchanges a still-valid refresh token for a new
    // access token, no password involved -- this is the actual mechanism that lets a
    // session outlive the access token's short (~1 hour) lifetime without asking the user
    // to log in again, as long as the refresh token itself (~30 days by default) is good.
    public Mono<JsonNode> refresh(String refreshToken) {
        return call("InitiateAuth", Map.of(
                "AuthFlow", "REFRESH_TOKEN_AUTH",
                "ClientId", clientId,
                "AuthParameters", Map.of("REFRESH_TOKEN", refreshToken)
        )).map(response -> response.get("AuthenticationResult"));
    }

    // Revokes every refresh token issued to this user (all devices/sessions), so a stolen
    // or leaked refresh-token cookie can't be replayed after an explicit logout. Best-effort
    // from AuthController's point of view -- see its comment there.
    public Mono<Void> globalSignOut(String accessToken) {
        return call("GlobalSignOut", Map.of("AccessToken", accessToken)).then();
    }

    private Mono<JsonNode> call(String target, Map<String, Object> body) {
        return webClient.post()
                .contentType(MediaType.valueOf("application/x-amz-json-1.1"))
                .header("X-Amz-Target", "AWSCognitoIdentityProviderService." + target)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(WebClientResponseException.class, ex -> Mono.error(toAuthException(ex)));
    }

    private CognitoAuthException toAuthException(WebClientResponseException ex) {
        try {
            JsonNode error = objectMapper.readTree(ex.getResponseBodyAsString());
            // Cognito's "__type" comes back as e.g. "NotAuthorizedException" or fully-qualified
            // like "...#NotAuthorizedException" depending on the API -- normalize to just the
            // simple name, which is all AuthController's status mapping actually checks.
            String type = error.path("__type").asText("");
            String simpleType = type.contains("#") ? type.substring(type.indexOf('#') + 1) : type;
            return new CognitoAuthException(simpleType, error.path("message").asText(ex.getMessage()));
        } catch (Exception parseFailure) {
            return new CognitoAuthException("", ex.getMessage());
        }
    }
}
