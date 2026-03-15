package com.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class RouteConfig {

    // Service URIs — in production these come from @Value / config server
    private static final String USER_SERVICE_URI  = "http://user-service:8081";
    private static final String ORDER_SERVICE_URI = "http://order-service:8082";

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()

            // ── User Service ──────────────────────────────────────
            .route("user-service-route", r -> r
                .path("/api/users/**")
                .and()
                .method(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .filters(f -> f
                    .addResponseHeader("X-Served-By", "user-service")
                    .addRequestHeader("X-Gateway-Request", "true")
                    // We'll plug AuthFilter and RateLimitFilter here next session
                )
                .uri(USER_SERVICE_URI)
            )

            // ── Order Service ─────────────────────────────────────
            .route("order-service-route", r -> r
                .path("/api/orders/**")
                .and()
                .method(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .filters(f -> f
                    .addResponseHeader("X-Served-By", "order-service")
                    .addRequestHeader("X-Gateway-Request", "true")
                )
                .uri(ORDER_SERVICE_URI)
            )

            .build();
    }
}
