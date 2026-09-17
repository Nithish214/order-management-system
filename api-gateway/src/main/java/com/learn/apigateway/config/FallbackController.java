package com.learn.apigateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

// Where a request lands when one of the route-level CircuitBreaker filters has its
// circuit OPEN (application.yml) -- meaning the target service has already failed or
// hung on enough recent calls that the breaker has stopped even trying, and is
// forwarding here instead, immediately, without waiting on the broken dependency at all.
//
// The response shape ({"message": "..."}) deliberately matches every other error
// response this app already produces (see OrderController's GlobalExceptionHandler) --
// CartSummary.jsx's existing `body.message || "..."` handling picks this up with no
// frontend changes needed for the order-creation path. GET endpoints (ProductsPage's
// /products and /stock calls) weren't originally checking response.ok before parsing --
// fixed alongside this feature specifically so hitting this fallback doesn't crash them
// on `.map is not a function` when the fallback's shape doesn't match a product/stock list.
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    // No method restriction (GET/POST/etc.) -- `forward:` preserves the original
    // request's HTTP method, and these routes cover both reads (GET /orders/{id}, GET
    // /stock) and writes (POST /orders, POST /stock/{id}/restock), so this needs to
    // catch whichever one actually arrives.
    @RequestMapping("/{service}")
    public Mono<ResponseEntity<Map<String, String>>> fallback(@PathVariable String service) {
        String friendlyName = "inventory-service".equals(service) ? "Inventory" : "Order";
        return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", friendlyName
                                + " service is temporarily unavailable. Please try again in a moment."))
        );
    }
}
