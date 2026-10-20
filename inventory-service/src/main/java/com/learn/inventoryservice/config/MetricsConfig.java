package com.learn.inventoryservice.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Same reasoning as Order Service's identical class -- see that file's comment. Without
// this, api-gateway's own health/metrics polling (SystemHealthSampler) counts as real
// traffic in this service's own request-rate numbers.
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
