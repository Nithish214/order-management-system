package com.learn.apigateway.health;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// One service's current state, as SystemHealthController hands it to the admin
// dashboard -- status for the "is it healthy right now" badge, componentDetails for
// "every metric" (whatever Spring Boot Actuator's own health indicators happen to be
// registered for that service -- db, diskSpace, etc. -- passed through generically
// rather than hand-coded per indicator type, so a new one showing up later just appears
// instead of needing a frontend change), requestsPerSecond/history for the live chart.
public record ServiceHealthSnapshot(
        String name,
        String status,
        Map<String, Object> componentDetails,
        double requestsPerSecond,
        List<RequestRatePoint> history
) {
    public record RequestRatePoint(Instant timestamp, double requestsPerSecond) {
    }
}
