package com.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    // Default limits — apply to all users unless overridden
    private int defaultMaxTokens    = 60;   // bucket capacity
    private int defaultRefillRate   = 60;   // tokens added per window
    private int defaultWindowSeconds= 60;   // window size in seconds

    // Per-role overrides
    // rate-limit.roles.ADMIN.max-tokens=1000
    private Map<String, RoleLimit> roles = Map.of(
        "ADMIN", new RoleLimit(1000, 1000, defaultWindowSeconds),
        "USER",  new RoleLimit(60, 60, defaultWindowSeconds)
    );

    @Data
    public static class RoleLimit {
        private int maxTokens;
        private int refillRate;
        private int windowSeconds;

        public RoleLimit() {}

        public RoleLimit(int maxTokens, int refillRate, int windowSeconds) {
            this.maxTokens     = maxTokens;
            this.refillRate    = refillRate;
            this.windowSeconds = windowSeconds;
        }
    }
}