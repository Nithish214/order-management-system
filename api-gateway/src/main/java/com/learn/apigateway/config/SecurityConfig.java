package com.learn.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

// This is the entire authorization policy for the whole application, in one place: which
// routes need a token at all, and which need more than that. Order Service and Inventory
// Service have no security code whatsoever -- they trust that the only way to reach them
// is through this Gateway, which is only true because their ports stay off the public
// internet at the network level (see the EC2 security group). This class is what turns
// "hidden" into "actually authorized."
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // A pure API gateway with no browser session/forms has no CSRF-vulnerable state to
                // protect -- CSRF protection exists for cookie-based sessions, not Bearer tokens.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // CORS preflight requests never carry the Authorization header -- that's by
                        // design, the browser hasn't been told it's allowed to send it yet. Requiring
                        // auth on OPTIONS would reject every preflight, which silently blocks the real
                        // request too, since the browser never proceeds past a failed preflight.
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Most specific rule next: restock additionally requires the "admin" group.
                        // hasAuthority checks for the exact "ROLE_admin" authority our converter below
                        // produces from the token's cognito:groups claim.
                        .pathMatchers(HttpMethod.POST, "/stock/*/restock").hasAuthority("ROLE_admin")
                        // Every other route just needs any validly-signed, unexpired token.
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    // Spring Security's default JWT->authorities mapping looks at "scope"/"scp" claims, which
    // Cognito access tokens don't meaningfully use here. This reads Cognito's own
    // "cognito:groups" claim instead and turns each group into a "ROLE_<group>" authority --
    // e.g. the "admin" group becomes "ROLE_admin", which is exactly what hasAuthority above checks.
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        Converter<Jwt, Collection<GrantedAuthority>> groupsConverter = jwt -> {
            List<String> groups = jwt.getClaimAsStringList("cognito:groups");
            if (groups == null) {
                return List.of();
            }
            return groups.stream()
                    .<GrantedAuthority>map(group -> new SimpleGrantedAuthority("ROLE_" + group))
                    .collect(Collectors.toList());
        };

        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(groupsConverter);

        // Spring Cloud Gateway runs on WebFlux (reactive), but JwtAuthenticationConverter is the
        // plain (non-reactive) Spring Security type -- this adapter wraps it into the Mono-returning
        // shape the reactive resource server actually needs.
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }
}
