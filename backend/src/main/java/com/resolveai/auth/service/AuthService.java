package com.resolveai.auth.service;

import com.resolveai.auth.dto.AuthResponse;
import com.resolveai.auth.dto.LoginRequest;
import com.resolveai.auth.dto.RefreshTokenRequest;
import com.resolveai.auth.dto.RegisterRequest;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.exception.EmailAlreadyExistsException;
import com.resolveai.auth.exception.InvalidTokenException;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.auth.security.JwtService;
import com.resolveai.auth.security.SecurityUser;
import com.resolveai.user.dto.UserResponse;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core Authentication Service handling user registration, credential authentication,
 * and JWT token issuance and renewal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String DEFAULT_ROLE_NAME = "EMPLOYEE";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /**
     * Registers a new user account with default EMPLOYEE role and hashed password.
     * Public registration cannot assign privileged roles (ADMIN, MANAGER, ENGINEER).
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("Email is already registered: " + normalizedEmail);
        }

        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException("Default role " + DEFAULT_ROLE_NAME + " not found in database"));

        String firstName = request.getEffectiveFirstName();
        if (firstName.isBlank()) {
            firstName = normalizedEmail.split("@")[0];
        }
        String lastName = request.getEffectiveLastName();

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(firstName)
                .lastName(lastName)
                .role(defaultRole)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Successfully registered user account with ID: {}", savedUser.getId());

        return UserResponse.fromEntity(savedUser);
    }

    /**
     * Authenticates user credentials and issues short-lived access JWT and refresh token.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        User user = securityUser.getUser();

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        long expiresInSeconds = jwtService.getAccessExpirationMs() / 1000;

        log.info("User {} authenticated successfully", normalizedEmail);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresInSeconds)
                .user(UserResponse.fromEntity(user))
                .build();
    }

    /**
     * Validates a refresh token and issues a new access token and renewed refresh token.
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (refreshToken == null || refreshToken.isBlank() || !jwtService.validateRefreshToken(refreshToken)) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        String email = jwtService.extractEmail(refreshToken);
        if (email == null || email.isBlank()) {
            throw new InvalidTokenException("Invalid refresh token claims");
        }

        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new InvalidTokenException("User associated with refresh token not found"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new InvalidTokenException("User account is disabled or inactive");
        }

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);
        long expiresInSeconds = jwtService.getAccessExpirationMs() / 1000;

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresInSeconds)
                .user(UserResponse.fromEntity(user))
                .build();
    }
}
