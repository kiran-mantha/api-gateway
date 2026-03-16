package com.userservice.service;

import com.userservice.entity.User;
import com.userservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository        userRepository;
    private final TokenBlacklistService blacklistService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiry-ms}")
    private long jwtExpiryMs;

    /**
     * Deletes user AND immediately revokes their current token.
     * The token's jti is passed in so we don't need to store
     * tokens in the DB at all.
     */
    @Transactional
    public void deleteUser(Long userId, String authHeader) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Revoke the token that came with this request
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            revokeToken(authHeader.substring(7));
        }

        userRepository.delete(user);
        log.info("User deleted and token revoked userId={}", userId);
    }

    /**
     * Logout — revokes the token without deleting the user.
     * Same mechanism, different trigger.
     */
    public void logout(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            revokeToken(authHeader.substring(7));
            log.info("User logged out, token revoked");
        }
    }

    private void revokeToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));

            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String jti        = claims.getId();
            long expiryMs     = claims.getExpiration().getTime();
            long remainingMs  = expiryMs - System.currentTimeMillis();

            if (remainingMs > 0) {
                blacklistService.blacklist(jti, remainingMs);
            }
        } catch (Exception e) {
            // Token already invalid — nothing to blacklist
            log.warn("Could not revoke token: {}", e.getMessage());
        }
    }
}