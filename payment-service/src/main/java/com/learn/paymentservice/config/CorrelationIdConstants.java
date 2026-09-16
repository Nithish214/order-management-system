package com.learn.paymentservice.config;

// Unlike Order Service/Inventory Service, this service has no REST endpoints of its own
// (see InventoryReservedListener's own class comment -- it's a pure Kafka consumer, never
// routed through api-gateway), so there's no servlet request to attach a filter to. This
// is just the shared header/MDC-key names, kept here so they match the same names the
// other two services use rather than each service inventing its own.
public final class CorrelationIdConstants {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdConstants() {
    }
}
