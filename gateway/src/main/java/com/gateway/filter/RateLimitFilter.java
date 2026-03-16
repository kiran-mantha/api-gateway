package com.gateway.filter;

import com.gateway.config.RateLimitProperties;
import com.gateway.config.RateLimitProperties.RoleLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long>           rateLimitScript;
    private final RateLimitProperties         properties;

    @Override
    public int getOrder() {
        return -90;   // after AuthFilter(-100), before LoggingFilter(-80)
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = exchange.getRequest()
                                .getHeaders()
                                .getFirst("X-User-Id");

        // No userId means AuthFilter let it through as public path
        // Public paths are not rate limited
        if (userId == null) {
            return chain.filter(exchange);
        }

        String role = exchange.getRequest()
                              .getHeaders()
                              .getFirst("X-User-Role");

        // Resolve limits for this user's role
        RoleLimit limit = properties.getRoles()
                                    .getOrDefault(role, defaultLimit());

        String bucketKey = "rate_limit:user:" + userId;

        List<String> keys = List.of(bucketKey);
        List<String> args = List.of(
            String.valueOf(limit.getMaxTokens()),
            String.valueOf(limit.getRefillRate()),
            String.valueOf(limit.getWindowSeconds()),
            "1"   // tokens requested per call
        );

        return redisTemplate
            .execute(rateLimitScript, keys, args)
            .next()   // script returns a single Long
            .flatMap(remaining -> {
                if (remaining < 0) {
                    // Bucket empty — reject
                    log.warn("Rate limit exceeded userId={} role={}", userId, role);
                    return rejectRequest(exchange, limit);
                }

                // Attach rate limit headers so clients can self-throttle
                exchange.getResponse().getHeaders()
                    .add("X-RateLimit-Limit",     String.valueOf(limit.getMaxTokens()));
                exchange.getResponse().getHeaders()
                    .add("X-RateLimit-Remaining", String.valueOf(remaining));
                exchange.getResponse().getHeaders()
                    .add("X-RateLimit-Window",    limit.getWindowSeconds() + "s");

                log.debug("Rate limit ok userId={} remaining={}", userId, remaining);
                return chain.filter(exchange);
            })
            .onErrorResume(e -> {
                // Redis is down — fail open (allow request) to avoid
                // taking down the whole system because of a cache failure
                log.error("Redis rate limit check failed, failing open: {}", e.getMessage());
                return chain.filter(exchange);
            });
    }

    private RoleLimit defaultLimit() {
        RoleLimit limit = new RoleLimit();
        limit.setMaxTokens(properties.getDefaultMaxTokens());
        limit.setRefillRate(properties.getDefaultRefillRate());
        limit.setWindowSeconds(properties.getDefaultWindowSeconds());
        return limit;
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, RoleLimit limit) {
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
}