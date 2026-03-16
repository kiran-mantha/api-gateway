package com.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.UUID;

@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String REQUEST_START_TIME    = "requestStartTime";

    @Override
    public int getOrder() {
        // Must be LOWER order number than AuthFilter to wrap it
        // LoggingFilter(-200) starts first, then AuthFilter(-100) runs inside it
        // This means our .doFinally() sees the status AuthFilter set
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Resolve or generate correlation ID
        String correlationId = request.getHeaders()
                                      .getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID()
                               .toString()
                               .replace("-", "")
                               .substring(0, 16);
        }

        final String finalCorrelationId = correlationId;

        exchange.getAttributes().put(REQUEST_START_TIME, System.currentTimeMillis());

        // Forward correlation ID downstream
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        // Add to response so client can reference it
        exchange.getResponse().getHeaders()
                .add(CORRELATION_ID_HEADER, finalCorrelationId);

        // PRE log
        log.info("[{}] --> {} {} | userId={} | ip={}",
            finalCorrelationId,
            request.getMethod(),
            request.getURI().getPath(),
            getHeaderOrDefault(request, "X-User-Id", "anonymous"),
            getClientIp(request)
        );

        return chain
            .filter(exchange.mutate().request(mutatedRequest).build())
            // doFinally fires on complete, error, AND cancel
            // It is guaranteed to run regardless of how the chain terminated
            .doFinally(signalType -> {
                Long startTime = exchange.getAttribute(REQUEST_START_TIME);
                long latencyMs = startTime != null
                    ? System.currentTimeMillis() - startTime
                    : -1;

                ServerHttpResponse response = exchange.getResponse();

                // getStatusCode() is reliable here because doFinally runs
                // after the response has been committed
                int statusCode = response.getStatusCode() != null
                    ? response.getStatusCode().value()
                    : 0;

                // signalType tells us HOW the chain ended
                // ON_COMPLETE = normal, ON_ERROR = exception, CANCEL = client disconnect
                String signal = signalType == SignalType.ON_ERROR   ? " [ERROR]"
                              : signalType == SignalType.CANCEL      ? " [CANCEL]"
                              : "";

                logResponse(finalCorrelationId, request, statusCode, latencyMs, signal);
            });
    }

    private void logResponse(String correlationId,
                             ServerHttpRequest request,
                             int statusCode,
                             long latencyMs,
                             String signal) {

        String msg = "[{}] <-- {} {}{} | status={} | latency={}ms";

        if (statusCode >= 500 || !signal.isEmpty()) {
            log.error(msg,
                correlationId,
                request.getMethod(),
                request.getURI().getPath(),
                signal,
                statusCode,
                latencyMs
            );
        } else if (statusCode >= 400) {
            log.warn(msg,
                correlationId,
                request.getMethod(),
                request.getURI().getPath(),
                signal,
                statusCode,
                latencyMs
            );
        } else {
            log.info(msg,
                correlationId,
                request.getMethod(),
                request.getURI().getPath(),
                signal,
                statusCode,
                latencyMs
            );
        }
    }

    private String getHeaderOrDefault(ServerHttpRequest request,
                                      String header,
                                      String defaultValue) {
        String value = request.getHeaders().getFirst(header);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private String getClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null
            ? request.getRemoteAddress().getAddress().getHostAddress()
            : "unknown";
    }
}