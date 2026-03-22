package com.gateway.filter;

import com.gateway.config.RateLimitProperties;
import com.gateway.config.RateLimitProperties.RoleLimit;
import com.gateway.ratelimit.InMemoryRateLimiter;
import com.gateway.ratelimit.RedisRateLimitExecutor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RedisRateLimitExecutor redisExecutor;
    private final RateLimitProperties properties;
    private final InMemoryRateLimiter inMemoryRateLimiter;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker redisCB;

    @PostConstruct
    public void init() {
        redisCB = circuitBreakerRegistry.circuitBreaker("redisCB");

        redisCB.getEventPublisher()
                .onStateTransition(event -> {
                    boolean isOpen = event.getStateTransition().getToState() == CircuitBreaker.State.OPEN;
                    log.warn(
                            "Redis CircuitBreaker: {} → {} | rate limiting via {}",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState(),
                            isOpen ? "IN-MEMORY FALLBACK" : "REDIS");
                })
                .onFailureRateExceeded(event -> log.warn(
                        "Redis failure rate {}% — circuit will trip open",
                        String.format("%.1f", event.getFailureRate())));
    }

    @Override
    public int getOrder() {
        return -90;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
            GatewayFilterChain chain) {

        String userId = getHeaderOrDefault(exchange, "X-User-Id", null);

        // Public path — no userId injected by AuthFilter
        if (userId == null) {
            return chain.filter(exchange);
        }

        String role = getHeaderOrDefault(exchange, "X-User-Role", "USER");

        RoleLimit limit = resolveLimit(role);

        return redisExecutor
                .executeRateLimit(
                        "rate_limit:user:" + userId,
                        limit.getMaxTokens(),
                        limit.getRefillRate(),
                        limit.getWindowSeconds())
                // Wire through circuit breaker so failures are recorded
                .transformDeferred(CircuitBreakerOperator.of(redisCB))
                .flatMap(remaining -> {
                    if (remaining < 0) {
                        log.warn("Rate limit exceeded userId={} source=redis", userId);
                        return rejectRequest(exchange, limit);
                    }
                    attachHeaders(exchange, limit, remaining, "redis");
                    return chain.filter(exchange);
                })
                .onErrorResume(ex -> {
                    if (ex instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
                        log.debug("Redis CB open — in-memory fallback for userId={}", userId);
                        return applyInMemoryLimit(exchange, chain, userId, role, "in-memory-cb-open");
                    }

                    // This now reliably catches ALL Redis failures including
                    // synchronous Lettuce connection exceptions
                    log.warn("Redis failed [{}] — in-memory fallback for userId={}",
                            ex.getClass().getSimpleName(), userId);

                    return applyInMemoryLimit(exchange, chain, userId, role,
                            "in-memory-redis-error");
                });
    }

    private Mono<Void> applyInMemoryLimit(ServerWebExchange exchange,
            GatewayFilterChain chain,
            String userId,
            String role,
            String source) {
        RoleLimit limit = resolveLimit(role);
        int remaining = inMemoryRateLimiter.tryConsume(userId, role);

        if (remaining < 0) {
            log.warn("Rate limit exceeded userId={} source={}", userId, source);
            return rejectRequest(exchange, limit);
        }

        attachHeaders(exchange, limit, (long) remaining, source);
        log.debug("Rate limit ok userId={} remaining={} source={}",
                userId, remaining, source);
        return chain.filter(exchange);
    }

    private void attachHeaders(ServerWebExchange exchange,
            RoleLimit limit,
            long remaining,
            String source) {
        exchange.getResponse().getHeaders()
                .add("X-RateLimit-Limit", String.valueOf(limit.getMaxTokens()));
        exchange.getResponse().getHeaders()
                .add("X-RateLimit-Remaining", String.valueOf(remaining));
        exchange.getResponse().getHeaders()
                .add("X-RateLimit-Window", limit.getWindowSeconds() + "s");
        exchange.getResponse().getHeaders()
                .add("X-RateLimit-Source", source);
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange,
            RoleLimit limit) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders()
                .add("Retry-After", String.valueOf(limit.getWindowSeconds()));

        String body = """
                {
                  "status": 429,
                  "error": "Too Many Requests",
                  "message": "Rate limit exceeded. Retry after %d seconds."
                }
                """.formatted(limit.getWindowSeconds());

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    private RoleLimit resolveLimit(String role) {
        return properties.getRoles()
                .getOrDefault(role, defaultLimit());
    }

    private RoleLimit defaultLimit() {
        RoleLimit limit = new RoleLimit();
        limit.setMaxTokens(properties.getDefaultMaxTokens());
        limit.setRefillRate(properties.getDefaultRefillRate());
        limit.setWindowSeconds(properties.getDefaultWindowSeconds());
        return limit;
    }

    private String getHeaderOrDefault(ServerWebExchange exchange,
            String headerName,
            String defaultValue) {
        String value = exchange.getRequest()
                .getHeaders()
                .getFirst(headerName);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}