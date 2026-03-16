package com.userservice.service;

import com.userservice.dto.LoginRequest;
import com.userservice.dto.LoginResponse;
import com.userservice.dto.RegisterRequest;
import com.userservice.entity.User;
import com.userservice.repository.UserRepository;
import com.userservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        // 1. Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                    new RuntimeException("Invalid email or password"));

        // 2. Check account is active
        if (!user.getIsActive()) {
            throw new RuntimeException("Account is disabled");
        }

        // 3. Verify password against bcrypt hash
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Same message as "user not found" — never reveal which one failed
            throw new RuntimeException("Invalid email or password");
        }

        // 4. Issue JWT
        String token = jwtUtil.generateToken(
            user.getId(),
            user.getEmail(),
            user.getRole()
        );

        log.info("Successful login for userId={} email={}", user.getId(), user.getEmail());

        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : "USER")
                .isActive(true)
                .build();

        User saved = userRepository.save(user);

        String token = jwtUtil.generateToken(
            saved.getId(),
            saved.getEmail(),
            saved.getRole()
        );

        log.info("New user registered userId={} email={}", saved.getId(), saved.getEmail());

        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(saved.getId())
                .email(saved.getEmail())
                .role(saved.getRole())
                .build();
    }
}