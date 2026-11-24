package com.sendw.mpos.shared.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Generate locally: an incoming ID must not inject arbitrary text into logs.
        String id = UUID.randomUUID().toString();
        String previous = MDC.get("requestId");
        MDC.put("requestId", id);
        response.setHeader("X-Request-ID", id);
        long started = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException error) {
            failed = true;
            throw error;
        } finally {
            String path = request.getRequestURI().replaceAll("[\\r\\n\\t]", "_");
            if (path.length() > 240) path = path.substring(0, 240);
            int status = failed ? 500 : response.getStatus();
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            if (status >= 400) log.warn("http_request method={} path={} status={} duration_ms={}", request.getMethod(), path, status, elapsed);
            else log.info("http_request method={} path={} status={} duration_ms={}", request.getMethod(), path, status, elapsed);
            if (previous == null) MDC.remove("requestId"); else MDC.put("requestId", previous);
        }
    }
}
