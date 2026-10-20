package com.learn.orderservice.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Without this, api-gateway's own health/metrics polling (SystemHealthSampler, every 5s)
// shows up as real traffic in this service's own http.server.requests count -- the act
// of monitoring the service inflates the very number it's trying to measure, a steady
// ~0.4 req/s floor that never reflects actual app usage. Found by actually watching the
// numbers: an idle service with zero real traffic still showed a suspiciously constant
// rate, which turned out to be exactly the sampler's own two calls (health + metrics)
// every five seconds. A plain MeterFilter bean is all Spring Boot needs -- it's
// auto-applied to the registry, no further wiring required.
@Configuration
public class MetricsConfig {

    @Bean
    public MeterFilter ignoreActuatorRequestsFilter() {
        return MeterFilter.deny(id -> {
            String uri = id.getTag("uri");
            return uri != null && uri.startsWith("/actuator");
        });
    }
}
