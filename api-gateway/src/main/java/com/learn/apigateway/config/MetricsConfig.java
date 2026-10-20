package com.learn.apigateway.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Same reasoning as the identical class in the three backend services -- see
// SystemHealthSampler's own comment. Here it's the Docker healthcheck (every 30s) rather
// than another service's polling, but the effect is the same: without this, hitting this
// service's own /actuator/health counts toward the very request rate SystemHealthSampler
// reports for it.
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
