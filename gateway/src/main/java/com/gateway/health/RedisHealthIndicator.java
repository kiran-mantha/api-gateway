package com.gateway.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component("redisRateLimit") // shows as "redisRateLimit" in /actuator/health
@RequiredArgsConstructor
public class RedisHealthIndicator implements ReactiveHealthIndicator {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public Mono<Health> health() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("redisCB");
        String cbState = cb.getState().name();

        CircuitBreaker.Metrics metrics = cb.getMetrics();

        return redisTemplate.execute(conn -> conn.ping())
                .next()
                .timeout(Duration.ofSeconds(2))
                .map(pong -> Health.up()
                        .withDetail("ping", pong)
                        .withDetail("circuitBreaker", cbState)
                        .withDetail("failureRate",
                                String.format("%.1f%%", metrics.getFailureRate()))
                        .withDetail("rateLimitSource", "redis")
                        .build())
                .onErrorResume(ex -> Mono.just(
                        Health.down()
                                .withDetail("error", ex.getMessage())
                                .withDetail("circuitBreaker", cbState)
                                .withDetail("failureRate",
                                        String.format("%.1f%%", metrics.getFailureRate()))
                                .withDetail("rateLimitSource", "in-memory-fallback")
                                .build()));
    }
}