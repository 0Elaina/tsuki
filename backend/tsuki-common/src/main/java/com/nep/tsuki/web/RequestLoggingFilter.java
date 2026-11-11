package com.nep.tsuki.web;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        String previousRequestId = MDC.get(REQUEST_ID_KEY);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_KEY, requestId);

        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            int status = response.getStatus();
            if (!completed && !response.isCommitted() && status < 400) {
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            }
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            try {
                log.info("method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), status, duration);
            } finally {
                if (previousRequestId == null) {
                    MDC.remove(REQUEST_ID_KEY);
                } else {
                    MDC.put(REQUEST_ID_KEY, previousRequestId);
                }
            }
        }
    }
}
