package com.gateway.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimitExecutor {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;

    /**
     * Executes the rate limit Lua script against Redis.
     *
     * Returns Mono.error() for ANY failure — connection refused,
     * timeout, or script error — so callers always get a clean
     * error signal they can handle with onErrorResume.
     *
     * The key insight: we wrap BOTH the assembly (Mono.defer)
     * AND the subscription (onErrorResume at this level) so
     * Lettuce's synchronous factory exception is caught here
     * before it can escape into the filter chain.
     */
    public Mono<Long> executeRateLimit(String key,
            int maxTokens,
            int refillRate,
            int windowSeconds) {
        return Mono
                // defer delays Lettuce's getReactiveConnection() call
                // to subscription time so it's inside the reactive chain
                .defer(() -> {
                    try {
                        return redisTemplate
                                .execute(
                                        rateLimitScript,
                                        List.of(key),
                                        List.of(
                                                String.valueOf(maxTokens),
                                                String.valueOf(refillRate),
                                                String.valueOf(windowSeconds),
                                                "1"))
                                .next()
                                // Also catch errors that happen during execution
                                .onErrorMap(ex -> new RedisOperationException(
                                        "Redis script execution failed", ex));
                    } catch (Exception ex) {
                        // Catch synchronous exceptions thrown by Lettuce
                        // during connection factory access — these escape
                        // Mono.defer() if not caught explicitly here
                        log.debug("Redis connection failed synchronously: {}",
                                ex.getMessage());
                        return Mono.error(
                                new RedisOperationException(
                                        "Redis connection failed", ex));
                    }
                })
                .onErrorResume(ex -> {
                    if (!(ex instanceof RedisOperationException)) {
                        log.debug("Redis unexpected error: {}", ex.getMessage());
                    }
                    return Mono.error(ex);
                });
    }

    // Typed wrapper so callers can distinguish Redis errors
    // from other errors in their onErrorResume
    public static class RedisOperationException extends RuntimeException {
        public RedisOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}