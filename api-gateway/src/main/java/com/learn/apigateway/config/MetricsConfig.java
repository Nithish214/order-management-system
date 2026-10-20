package com.learn.apigateway.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

// Excludes every "this is monitoring, not real traffic" path from this service's own
// http.server.requests metric -- /actuator/** for the same reasoning as the identical
// class in the three backend services (the Docker healthcheck, every 30s), plus two
// paths unique to this service: /system/health is SystemHealthSampler's own admin-
// dashboard endpoint, polled by SystemHealthPanel.jsx every 5s -- without this, simply
// having the dashboard open counts as app traffic in the very number it's displaying,
// a steady ~0.2-0.4 req/s floor that's really just the dashboard watching itself. Found
// by the dashboard's own first real user, seeing exactly that floor with nothing else
// going on. /health/services is the older, simpler UP/DOWN check (CloudSwitch app,
// Docker healthchecks) -- the same reasoning applies: it's infrastructure polling, not
// a customer using the app.
@Configuration
public class MetricsConfig {

    private static final Set<String> IGNORED_EXACT_PATHS = Set.of("/system/health", "/health/services");

    @Bean
    public MeterFilter ignoreMonitoringRequestsFilter() {
        return MeterFilter.deny(id -> {
            String uri = id.getTag("uri");
            return uri != null && (uri.startsWith("/actuator") || IGNORED_EXACT_PATHS.contains(uri));
        });
    }
}
