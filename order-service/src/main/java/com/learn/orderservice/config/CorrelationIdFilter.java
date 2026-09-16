package com.learn.orderservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// The servlet-side half of api-gateway's CorrelationIdFilter: reads the X-Correlation-Id
// header the Gateway already attached to every request (see that class's own comment for
// the full end-to-end picture) and puts it in MDC, which is what actually makes it show
// up on every log line this request produces (see application.yml's logging.pattern.level,
// which embeds %X{correlationId}) -- MDC alone does nothing on its own, it's just a
// per-thread map that the log pattern has to be told to read from.
//
// The cleanup in `finally` matters more than it looks: Tomcat reuses request-handling
// threads from a pool across many different requests over the life of the application.
// Without clearing this, a thread that handled request A would still have request A's
// correlation id sitting in its MDC when it later picks up unrelated request B, silently
// mislabeling B's log lines with A's id.
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        // Falls back to generating one rather than leaving MDC empty -- covers direct
        // calls to this service that skip the Gateway entirely (a local curl during
        // development, an internal health check), so log lines from those still carry
        // *some* id rather than a blank gap in the pattern.
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
