package com.sellsync.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP 요청/응답 로깅 및 MDC Correlation ID 필터
 *
 * <p>모든 요청에 고유 traceId를 부여하고, 요청/응답 정보를 로깅합니다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID, traceId);
        response.setHeader("X-Trace-Id", traceId);

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String path = queryString != null ? uri + "?" + queryString : uri;

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            if (status >= 500) {
                log.error("[{}] {} {} → {} ({}ms)", traceId, method, path, status, duration);
            } else if (status >= 400) {
                log.warn("[{}] {} {} → {} ({}ms)", traceId, method, path, status, duration);
            } else {
                log.info("[{}] {} {} → {} ({}ms)", traceId, method, path, status, duration);
            }

            MDC.remove(TRACE_ID);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }
}
