package com.gateway.ratelimit;

import com.gateway.config.RateLimitProperties;
import com.gateway.config.RateLimitProperties.RoleLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryRateLimiter {

    private final RateLimitProperties properties;

    // userId → bucket
    private final ConcurrentHashMap<String, UserBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Attempt to consume one token for this user.
     * Returns remaining tokens, or -1 if rejected.
     */
    public int tryConsume(String userId, String role) {
        RoleLimit limit = properties.getRoles()
                .getOrDefault(role, defaultLimit());

        UserBucket bucket = buckets.computeIfAbsent(
                userId,
                k -> new UserBucket(limit.getMaxTokens(),
                        limit.getWindowSeconds()));

        return bucket.tryConsume();
    }

    /**
     * Evict buckets that have been inactive longer than 2 windows.
     * Runs every 60 seconds — prevents unbounded memory growth.
     * Without this, every userId that ever made a request stays in memory forever.
     */
    @Scheduled(fixedDelay = 60_000)
    public void evictStaleBuckets() {
        long now = System.currentTimeMillis();
        int before = buckets.size();

        buckets.entrySet().removeIf(entry -> entry.getValue().isStale(now));

        int evicted = before - buckets.size();
        if (evicted > 0) {
            log.debug("Evicted {} stale in-memory rate limit buckets", evicted);
        }
    }

    public int getBucketCount() {
        return buckets.size();
    }

    private RoleLimit defaultLimit() {
        RoleLimit limit = new RoleLimit();
        limit.setMaxTokens(properties.getDefaultMaxTokens());
        limit.setRefillRate(properties.getDefaultRefillRate());
        limit.setWindowSeconds(properties.getDefaultWindowSeconds());
        return limit;
    }

    // ── Inner class — one per userId ───────────────────────────────

    static class UserBucket {

        private final int maxTokens;
        private final long windowMs;
        private final Object lock = new Object();

        private int tokens;
        private long windowStart;
        private long lastAccess;

        UserBucket(int maxTokens, int windowSeconds) {
            this.maxTokens = maxTokens;
            this.windowMs = windowSeconds * 1000L;
            this.tokens = maxTokens;
            this.windowStart = System.currentTimeMillis();
            this.lastAccess = System.currentTimeMillis();
        }

        int tryConsume() {
            synchronized (lock) {
                long now = System.currentTimeMillis();
                lastAccess = now;

                // If the window has passed, reset the bucket
                if (now - windowStart >= windowMs) {
                    tokens = maxTokens;
                    windowStart = now;
                }

                if (tokens <= 0) {
                    return -1; // rejected
                }

                tokens--;
                return tokens; // remaining
            }
        }

        boolean isStale(long now) {
            // Stale if not accessed for 2 full windows
            return (now - lastAccess) > (windowMs * 2);
        }
    }
}