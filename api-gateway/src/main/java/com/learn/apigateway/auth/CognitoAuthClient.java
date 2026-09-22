package com.learn.apigateway.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
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

    private static final Logger log = LoggerFactory.getLogger(CognitoAuthClient.class);

    private final WebClient webClient;
    // Separate client, separate base URL -- the Hosted UI domain's /oauth2/token endpoint
    // is a completely different surface from cognito-idp.<region>.amazonaws.com above (see
    // this field's own comment further down, and application.yml's cognito.domain
    // comment). Standard OAuth2 form-encoded request/JSON response, none of the
    // "application/x-amz-json-1.1" awkwardness webClient above exists to work around.
    private final WebClient oauth2WebClient;
    private final ObjectMapper objectMapper;
    private final String clientId;

    public CognitoAuthClient(
            @Value("${cognito.region}") String region,
            @Value("${cognito.client-id}") String clientId,
            @Value("${cognito.domain}") String domain,
            ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.objectMapper = objectMapper;
        this.oauth2WebClient = WebClient.builder().baseUrl(domain).build();
        // Cognito's JSON API uses "application/x-amz-json-1.1" as its content type --
        // genuinely just JSON, but a non-standard media type string, and Spring's default
        // Jackson codecs only recognize a fixed allowlist (application/json,
        // application/*+json). Without this, WebClient refuses to even attempt encoding
        // the request body or decoding the response, and throws
        // UnsupportedMediaTypeException before a single byte reaches the network -- this
        // registers that media type on the same Jackson codecs as an equal to
        // application/json, both directions.
        MediaType awsJson = MediaType.valueOf("application/x-amz-json-1.1");
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    configurer.customCodecs().register(new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON, awsJson));
                    configurer.customCodecs().register(new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON, awsJson));
                })
                .build();
        this.webClient = WebClient.builder()
                .baseUrl("https://cognito-idp." + region + ".amazonaws.com/")
                .exchangeStrategies(strategies)
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

    // The other half of Google sign-in (see AuthController's /auth/google/callback): the
    // frontend already redirected the browser to Cognito's Hosted UI, the user picked
    // "Continue with Google" and approved there, and Cognito redirected back with a
    // one-time authorization code. This exchanges that code for real tokens -- the one
    // step that has to happen server-side, so the resulting refresh token can go straight
    // into the same httpOnly cookie login()/refresh() above already use, never touching
    // browser JavaScript.
    //
    // No PKCE (code_verifier) here -- tried first, but Cognito rejected the exchange with
    // invalid_grant on every attempt specifically for the federated (Google) path, even
    // with byte-for-byte correct redirect_uri and code_verifier values (see google.js's own
    // comment on the frontend side). Dropping it is a reasonable trade regardless: this
    // exchange already only ever happens server-side, never in browser JS, which is the
    // exact class of interception PKCE exists to guard against for a public client.
    public Mono<JsonNode> exchangeAuthorizationCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        return oauth2WebClient.post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(WebClientResponseException.class, ex -> Mono.error(toOAuth2AuthException(ex)));
    }

    // Standard OAuth2 error shape ({"error": "...", "error_description": "..."}) --
    // deliberately NOT toAuthException below, which parses Cognito's own proprietary JSON
    // API error shape ({"__type": "...", "message": "..."}) that InitiateAuth/
    // GlobalSignOut use. The /oauth2/token endpoint is the standard OAuth2 surface, not
    // that proprietary API, so it fails in this differently-shaped way instead.
    private CognitoAuthException toOAuth2AuthException(WebClientResponseException ex) {
        // WARN, not silently swallowed -- an auth failure here is either a real user-facing
        // problem (worth being able to explain after the fact) or a misconfiguration
        // (redirect_uri mismatch, a stale/reused code, PKCE verifier mismatch) that's
        // otherwise invisible: AuthController's own error handling deliberately returns an
        // empty-body 401/400 to the browser, so this log line is the only place the actual
        // reason ever surfaces.
        log.warn("Google/Cognito OAuth2 token exchange failed: HTTP {} body={}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
        try {
            JsonNode error = objectMapper.readTree(ex.getResponseBodyAsString());
            return new CognitoAuthException(
                    error.path("error").asText(""),
                    error.path("error_description").asText(ex.getMessage()));
        } catch (Exception parseFailure) {
            return new CognitoAuthException("", ex.getMessage());
        }
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
