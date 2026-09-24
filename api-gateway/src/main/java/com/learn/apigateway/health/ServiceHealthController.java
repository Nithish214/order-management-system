package com.learn.apigateway.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

// /actuator/health (SecurityConfig) only ever reports THIS process's own health -- a JVM
// that's up and accepting connections can say "UP" while order-service or inventory-service
// behind it are still crash-looping (the exact gap fix-containers-on-boot.sh works around
// from the EC2 side by checking those services' own ports directly, not the gateway's).
// This endpoint does the same check from inside the app instead, so a client (the EC2/RDS
// monitor app) can see it without SSHing in: it reuses ORDER_SERVICE_URL/INVENTORY_SERVICE_URL
// (the same env vars the route config already resolves against, see application.yml) to hit
// each service's own /actuator/health directly over the Docker network, the same way the
// Compose healthchecks do from outside.
@RestController
@RequestMapping("/health")
public class ServiceHealthController {

    private final WebClient webClient;
    private final String orderServiceUrl;
    private final String inventoryServiceUrl;

    public ServiceHealthController(
            @Value("${order.service.url:http://localhost:8081}") String orderServiceUrl,
            @Value("${inventory.service.url:http://localhost:8082}") String inventoryServiceUrl) {
        this.orderServiceUrl = orderServiceUrl;
        this.inventoryServiceUrl = inventoryServiceUrl;
        this.webClient = WebClient.builder().build();
    }

    @GetMapping("/services")
    public Mono<Map<String, String>> serviceHealth() {
        return Mono.zip(
                checkHealth(orderServiceUrl),
                checkHealth(inventoryServiceUrl)
        ).map(statuses -> Map.of(
                // If this method is running at all, the gateway itself is obviously up --
                // included anyway so the client has one flat shape for all three containers
                // instead of treating "am I even connected" as a special case.
                "apiGateway", "UP",
                "orderService", statuses.getT1(),
                "inventoryService", statuses.getT2()
        ));
    }

    private Mono<String> checkHealth(String baseUrl) {
        return webClient.get()
                .uri(baseUrl + "/actuator/health")
                .retrieve()
                .toBodilessEntity()
                // 3s, not the 5s the route-level CircuitBreaker timeout uses -- this call
                // has nowhere near as far to travel (container to container, no Postgres/
                // Kafka work behind it), so a slow reply here is a stronger, earlier signal
                // that something's genuinely wrong rather than just momentarily busy.
                .timeout(Duration.ofSeconds(3))
                .map(response -> "UP")
                // Covers connection refused (container not listening yet), timeout (hung),
                // and a non-2xx health response (actuator itself reports DOWN) all the same
                // way -- the client only needs "is this one working right now," not why not.
                .onErrorReturn("DOWN");
    }
}
