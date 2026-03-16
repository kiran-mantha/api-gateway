package com.userservice.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)   // run before everything else in user-service
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY               = "correlationId";

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest  httpRequest  = (HttpServletRequest)  request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);

        try {
            // Put into MDC — Logback automatically includes MDC values
            // in every log line from this thread
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(MDC_KEY, correlationId);
                // Echo it back in the response as well
                httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            }

            chain.doFilter(request, response);

        } finally {
            // Always clean up MDC — threads are reused from a pool,
            // leftover MDC values leak into the next request
            MDC.remove(MDC_KEY);
        }
    }
}