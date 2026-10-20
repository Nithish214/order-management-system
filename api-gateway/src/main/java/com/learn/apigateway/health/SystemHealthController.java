package com.learn.apigateway.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// The admin-only system-health dashboard's one endpoint -- a snapshot of
// SystemHealthSampler's in-memory state, admin-gated by SecurityConfig
// (.pathMatchers("/system/health")), unlike /health/services above, which stays
// permitAll for the Docker healthcheck/CloudSwitch app and only ever reports a plain
// three-way UP/DOWN with no further detail.
@RestController
public class SystemHealthController {

    private final SystemHealthSampler sampler;

    public SystemHealthController(SystemHealthSampler sampler) {
        this.sampler = sampler;
    }

    @GetMapping("/system/health")
    public ResponseEntity<List<ServiceHealthSnapshot>> systemHealth() {
        return ResponseEntity.ok(sampler.currentSnapshots());
    }
}
