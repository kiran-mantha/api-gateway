package com.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:jti:";

    /**
     * Blacklists a token by its jti.
     * TTL is set to the token's remaining lifetime so Redis
     * auto-cleans entries for already-expired tokens.
     */
    public void blacklist(String jti, long remainingLifetimeMs) {
        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(
            key,
            "revoked",
            Duration.ofMillis(remainingLifetimeMs)
        );
        log.info("Token blacklisted jti={}", jti);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey(BLACKLIST_PREFIX + jti)
        );
    }
}