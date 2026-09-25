package com.learn.orderservice.controller;

import com.learn.orderservice.dto.AnalyticsSummaryResponse;
import com.learn.orderservice.exception.ForbiddenException;
import com.learn.orderservice.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Admin-only. The Gateway already rejects non-admins outright for /analytics/** (see
// SecurityConfig), but this checks X-User-Is-Admin itself too -- same header
// OrderController's "admins can't place orders" rule reads. Sales figures are exactly the
// kind of data where one misconfigured Gateway rule shouldn't be the only thing standing
// between any logged-in shopper and the whole business's numbers.
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary(
            @RequestHeader(value = "X-User-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        if (!isAdmin) {
            throw new ForbiddenException("Analytics is only available to admins");
        }
        return ResponseEntity.ok(analyticsService.getSummary());
    }
}
