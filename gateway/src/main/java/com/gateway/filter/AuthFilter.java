package com.gateway.filter;

import com.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:jti:";

    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/auth/login",
        "/api/auth/register",
        "/actuator"
    );

    // Filters run in order — auth must be FIRST (lowest order number)
    @Override
    public int getOrder() {
        return -100;    // runs before rate limiting (-90) and logging (-80)
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        log.debug("AuthFilter processing: {} {}", request.getMethod(), path);

        // Skip auth for public routes
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Check Authorization header exists
        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return rejectRequest(exchange, HttpStatus.UNAUTHORIZED,
                "Missing Authorization header");
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // Check Bearer format
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return rejectRequest(exchange, HttpStatus.UNAUTHORIZED,
                "Invalid Authorization format. Expected: Bearer <token>");
        }

        String token = authHeader.substring(7); // strip "Bearer "

        try {
            Claims claims = jwtUtil.extractAllClaims(token);

            String jti = claims.getId();

            // Check Redis blacklist before allowing the request
            return redisTemplate.hasKey(BLACKLIST_PREFIX + jti)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        log.warn("Revoked token used jti={} path={}", jti, path);
                        return rejectRequest(exchange, HttpStatus.UNAUTHORIZED,
                            "Token has been revoked");
                    }

                    // Token clean — forward user context downstream
                    ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-User-Id",    claims.getSubject())
                        .header("X-User-Role",  claims.get("role",  String.class))
                        .header("X-User-Email", claims.get("email", String.class))
                        .build();

                    log.debug("Auth passed userId={} jti={}",
                        claims.getSubject(), jti);

                    return chain.filter(
                        exchange.mutate().request(mutatedRequest).build());
                });

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT path={}", path);
            return rejectRequest(exchange, HttpStatus.UNAUTHORIZED,
                "Token has expired");

        } catch (JwtException e) {
            log.warn("Invalid JWT path={}: {}", path, e.getMessage());
            return rejectRequest(exchange, HttpStatus.UNAUTHORIZED,
                "Invalid token");

        } catch (Exception e) {
            log.error("Unexpected auth error path={}", path, e);
            return rejectRequest(exchange, HttpStatus.INTERNAL_SERVER_ERROR,
                "Authentication error");
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Write an error response and terminate the filter chain.
     * Because the gateway is reactive, we must write the response manually
     * instead of throwing an exception — there is no servlet to catch it.
     */
    private Mono<Void> rejectRequest(ServerWebExchange exchange,
                                      HttpStatus status,
                                      String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
            {
              "status": %d,
              "error": "%s",
              "message": "%s"
            }
            """.formatted(status.value(), status.getReasonPhrase(), message);

        DataBuffer buffer = response.bufferFactory()
            .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}
