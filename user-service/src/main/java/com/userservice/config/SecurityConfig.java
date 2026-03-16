package com.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // Expose BCryptPasswordEncoder as a bean
    // AuthService injects this — never instantiate it with new()
    // because Spring caches the instance (BCrypt is expensive to init)
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);   // strength 12 = ~300ms hash time
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — we're stateless (JWT), no cookies
            .csrf(AbstractHttpConfigurer::disable)
            // Permit everything — gateway already enforces auth
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}