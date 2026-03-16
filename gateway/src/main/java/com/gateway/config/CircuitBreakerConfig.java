package com.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CircuitBreakerConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    public void registerEventListeners() {
        // Attach listeners to each circuit breaker instance
        registerListeners("userServiceCB");
        registerListeners("orderServiceCB");
    }

    private void registerListeners(String name) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);

        cb.getEventPublisher()
            .onStateTransition(event -> log.warn(
                "CircuitBreaker [{}] state: {} → {}",
                name,
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState()
            ))
            .onFailureRateExceeded(event -> log.warn(
                "CircuitBreaker [{}] failure rate exceeded: {}%",
                name,
                String.format("%.1f", event.getFailureRate())
            ))
            .onSlowCallRateExceeded(event -> log.warn(
                "CircuitBreaker [{}] slow call rate exceeded: {}%",
                name,
                String.format("%.1f", event.getSlowCallRate())
            ))
            .onCallNotPermitted(event -> log.debug(
                "CircuitBreaker [{}] call rejected — circuit is OPEN", name
            ));
    }
}