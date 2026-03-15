package com.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RouteConfig {

    // Service URIs — in production these come from @Value / config server
    @Value("${services.gateway.uri}")
    private String gatewayUri;
    @Value("${services.user-service.uri}")
    private String userServiceUri;
    @Value("${services.order-service.uri}")
    private String orderServiceUri;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()

            .route("auth-route", r -> r
                .path("/api/auth/**")
                .filters(f -> f
                    .addResponseHeader("X-Served-By", "gateway-auth")
                )
                .uri("http://localhost:8080")
            )

            // ── User Service ──────────────────────────────────────
            .route("user-service-route", r -> r
                .path("/api/users/**")
                .and()
                .method(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .filters(f -> f
                    .addResponseHeader("X-Served-By", "user-service")
                    .addRequestHeader("X-Gateway-Request", "true")
                )
                .uri(userServiceUri)
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
                .uri(orderServiceUri)
            )

            .build();
    }
}
