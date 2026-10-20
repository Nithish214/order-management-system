package com.learn.apigateway.health;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Polls every backend service's own Actuator endpoints every 5s and keeps a short rolling
// history in memory -- the same WebClient-to-/actuator/health-over-the-Docker-network
// pattern ServiceHealthController already uses for its simple UP/DOWN check, extended to
// also read /actuator/metrics/http.server.requests (a cumulative counter since that JVM
// started) and turn two samples of it into an actual rate. Backs the admin-only
// GET /system/health (SystemHealthController) -- in-memory only, lost on a gateway
// restart, which is fine: it's a live operational view, not data anyone needs to persist.
@Component
public class SystemHealthSampler {

    // 5 minutes of history at 5s resolution -- enough to see a real trend or a spike,
    // short enough that the chart stays readable and this never grows unbounded.
    private static final int MAX_HISTORY = 60;
    private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final MeterRegistry meterRegistry;
    private final Map<String, String> serviceUrls;
    // One entry per service name, created once at startup (see the constructor) rather
    // than lazily on first sample -- avoids any race on map insertion between the
    // scheduler thread and whatever reads it.
    private final Map<String, Tracker> trackers = new ConcurrentHashMap<>();

    public SystemHealthSampler(
            WebClient.Builder webClientBuilder,
            MeterRegistry meterRegistry,
            @Value("${order.service.url:http://localhost:8081}") String orderServiceUrl,
            @Value("${inventory.service.url:http://localhost:8082}") String inventoryServiceUrl,
            @Value("${payment.service.url:http://localhost:8083}") String paymentServiceUrl
    ) {
        this.webClient = webClientBuilder.build();
        this.meterRegistry = meterRegistry;
        // LinkedHashMap, not a plain Map.of -- iteration order here becomes the order
        // services appear in the dashboard, and "gateway first, then the three it talks
        // to" reads more naturally than whatever order a hash map happens to settle on.
        this.serviceUrls = new LinkedHashMap<>();
        serviceUrls.put("order-service", orderServiceUrl);
        serviceUrls.put("inventory-service", inventoryServiceUrl);
        serviceUrls.put("payment-service", paymentServiceUrl);
        for (String name : serviceUrls.keySet()) {
            trackers.put(name, new Tracker());
        }
        // api-gateway itself -- no URL, no HTTP round trip at all (see sampleSelf below):
        // its own request count comes straight out of the MeterRegistry already in this
        // process, and health is the same "if this code is running, it's up" shorthand
        // ServiceHealthController already uses, so it gets its own Tracker but never
        // touches serviceUrls/webClient.
        trackers.put("api-gateway", new Tracker());
    }

    @Scheduled(fixedRate = 5000)
    void sample() {
        sampleSelf();
        serviceUrls.forEach(this::sampleRemote);
    }

    // api-gateway's own request count, read directly from the same MeterRegistry Spring
    // Boot's WebFlux metrics auto-configuration is already recording into -- no network
    // call, and nothing new exposed on this service's own (genuinely public-facing, see
    // SecurityConfig) HTTP surface to get it.
    private void sampleSelf() {
        double totalCount = meterRegistry.find("http.server.requests").timers().stream()
                .mapToDouble(Timer::count)
                .sum();
        Tracker tracker = trackers.get("api-gateway");
        recordSample(tracker, totalCount);
        tracker.status = "UP";
        tracker.componentDetails = Map.of();
    }

    private void sampleRemote(String name, String baseUrl) {
        Tracker tracker = trackers.get(name);

        webClient.get().uri(baseUrl + "/actuator/health")
                .retrieve().bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(3))
                // Explicitly flips to DOWN on failure (connection refused, timeout, a
                // non-2xx health response) rather than silently keeping whatever status
                // was last known -- same "any failure reads as DOWN" reasoning as
                // ServiceHealthController's own onErrorReturn, just a side effect here
                // instead of a return value, since this is a fire-and-forget subscribe,
                // not something a caller is awaiting.
                .doOnError(e -> {
                    tracker.status = "DOWN";
                    tracker.componentDetails = Map.of();
                })
                .onErrorResume(e -> Mono.empty())
                .subscribe(health -> {
                    tracker.status = health.path("status").asText("UNKNOWN");
                    tracker.componentDetails = flattenComponents(health.path("components"));
                });

        webClient.get().uri(baseUrl + "/actuator/metrics/http.server.requests")
                .retrieve().bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(3))
                .map(SystemHealthSampler::extractCount)
                // A 404 here is a real, valid reading -- "zero requests recorded yet" --
                // not a failure, for a service that never receives any HTTP traffic other
                // than this very sampler's own /actuator/** calls (payment-service: no
                // Gateway route at all, reached only via Kafka). Those calls are the
                // exact thing excluded from this metric (see MetricsConfig), so its
                // http.server.requests timer has zero non-denied samples -- the metric
                // NAME is still pre-registered (shows up in the bare /actuator/metrics
                // listing) but the detail query 404s until something records a real
                // sample against it. Recording that as count=0 here, not skipping the
                // tick, is what makes this service's chart show an honest flat zero
                // line instead of staying permanently blank.
                //
                // Any OTHER failure (connection refused, timeout, a real 5xx) still
                // falls through to onErrorResume below and skips the tick entirely --
                // treating THOSE as count=0 would reset the baseline, so a service that
                // comes back after a genuine outage would compute its rate against zero
                // and report one enormous, wrong spike for its first recovered sample.
                .onErrorResume(
                        e -> e instanceof WebClientResponseException wcre && wcre.getStatusCode().value() == 404,
                        e -> Mono.just(0.0))
                .onErrorResume(e -> Mono.empty())
                .subscribe(count -> recordSample(tracker, count));
    }

    // /actuator/metrics/<name>, queried with no tag selector, already aggregates every
    // tag combination (per-route, per-status, per-outcome) into one COUNT -- no manual
    // summing needed here the way sampleSelf() has to do against the raw MeterRegistry.
    private static double extractCount(JsonNode metricsResponse) {
        for (JsonNode measurement : metricsResponse.path("measurements")) {
            if ("COUNT".equals(measurement.path("statistic").asText())) {
                return measurement.path("value").asDouble(0);
            }
        }
        return 0;
    }

    // Status + whatever "details" each indicator (db, diskSpace, ping, ...) happens to
    // publish -- passed through as plain maps rather than parsed into a fixed set of
    // known indicator types, so a health indicator added to a service later just shows
    // up here with no change needed on this end or the frontend's.
    private static Map<String, Object> flattenComponents(JsonNode components) {
        Map<String, Object> result = new LinkedHashMap<>();
        components.fields().forEachRemaining(entry -> {
            Map<String, Object> component = new LinkedHashMap<>();
            component.put("status", entry.getValue().path("status").asText("UNKNOWN"));
            JsonNode details = entry.getValue().path("details");
            if (!details.isMissingNode()) {
                Map<String, Object> detailMap = new LinkedHashMap<>();
                details.fields().forEachRemaining(d -> detailMap.put(d.getKey(), d.getValue().asText()));
                component.put("details", detailMap);
            }
            result.put(entry.getKey(), component);
        });
        return result;
    }

    // Turns this tick's cumulative counter reading into a rate and appends it to the
    // rolling history -- the one piece of logic sampleSelf() and sampleRemote() both
    // need, so it lives here once rather than duplicated in each.
    private static void recordSample(Tracker tracker, double currentCount) {
        Instant now = Instant.now();
        double rps = 0;
        // previousCount is null exactly once per tracker, on its very first ever sample
        // -- there's no prior reading yet to take a delta against, so this tick
        // correctly reports 0 rather than guessing. Self-corrects on the next tick, 5s
        // later, same as any brand-new counter would.
        if (tracker.previousCount != null && tracker.previousSampleTime != null) {
            double elapsedSeconds = Duration.between(tracker.previousSampleTime, now).toMillis() / 1000.0;
            if (elapsedSeconds > 0) {
                // max(0, ...) -- a counter can only go up; a negative delta here would
                // only mean the service restarted (its in-memory counter reset to 0),
                // not that it somehow served negative requests.
                rps = Math.max(0, (currentCount - tracker.previousCount) / elapsedSeconds);
            }
        }
        tracker.previousCount = currentCount;
        tracker.previousSampleTime = now;

        synchronized (tracker.history) {
            tracker.history.addLast(new ServiceHealthSnapshot.RequestRatePoint(now, rps));
            while (tracker.history.size() > MAX_HISTORY) {
                tracker.history.removeFirst();
            }
        }
    }

    // Read by SystemHealthController on every GET /system/health -- a snapshot copy, not
    // a live reference, so the scheduler appending to `history` concurrently (every 5s,
    // on its own thread) can never be observed mid-mutation by a request thread.
    public List<ServiceHealthSnapshot> currentSnapshots() {
        return trackers.entrySet().stream()
                .map(entry -> {
                    Tracker tracker = entry.getValue();
                    List<ServiceHealthSnapshot.RequestRatePoint> historyCopy;
                    synchronized (tracker.history) {
                        historyCopy = List.copyOf(tracker.history);
                    }
                    double latestRps = historyCopy.isEmpty() ? 0 : historyCopy.get(historyCopy.size() - 1).requestsPerSecond();
                    return new ServiceHealthSnapshot(
                            entry.getKey(),
                            tracker.status == null ? "UNKNOWN" : tracker.status,
                            tracker.componentDetails == null ? Map.of() : tracker.componentDetails,
                            latestRps,
                            historyCopy
                    );
                })
                .toList();
    }

    private static final class Tracker {
        volatile String status;
        volatile Map<String, Object> componentDetails;
        volatile Double previousCount;
        volatile Instant previousSampleTime;
        final Deque<ServiceHealthSnapshot.RequestRatePoint> history = new ArrayDeque<>();
    }
}
