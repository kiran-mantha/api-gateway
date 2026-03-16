package com.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    // Load the Lua script as a bean so it's compiled once
    // and reused across all rate limit checks
    @Bean
    public RedisScript<Long> rateLimitScript() {
        return RedisScript.of(
            new ClassPathResource("scripts/rate_limit.lua"),
            Long.class
        );
    }
}