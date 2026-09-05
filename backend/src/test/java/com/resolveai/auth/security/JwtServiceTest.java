package com.resolveai.auth.security;

import com.resolveai.auth.config.JwtProperties;
import com.resolveai.auth.entity.Role;
import com.resolveai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("dGVzdF9zZWNyZXRfa2V5X2Zvcl91bml0X3Rlc3RpbmdfcHJvamVjdF9yZXNvbHZlYWlfMjAyNmVudGVycHJpc2Vfc2VjdXJlX2tleQ==");
        jwtProperties.setAccessExpirationMs(900000L);
        jwtProperties.setRefreshExpirationMs(604800000L);

        jwtService = new JwtService(jwtProperties);

        Role role = Role.builder()
                .id(1L)
                .name("EMPLOYEE")
                .description("Default employee")
                .build();

        testUser = User.builder()
                .id(42L)
                .email("test.unit@resolveai.internal")
                .firstName("Test")
                .lastName("Unit")
                .role(role)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Generate and validate access token")
    void testGenerateAndValidateAccessToken() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateAccessToken(token)).isTrue();
        assertThat(jwtService.validateRefreshToken(token)).isFalse();
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractEmail(token)).isEqualTo("test.unit@resolveai.internal");
        assertThat(jwtService.extractRole(token)).isEqualTo("EMPLOYEE");
        assertThat(jwtService.extractTokenType(token)).isEqualTo(JwtService.TOKEN_TYPE_ACCESS);
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("Generate and validate refresh token")
    void testGenerateAndValidateRefreshToken() {
        String token = jwtService.generateRefreshToken(testUser);

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateRefreshToken(token)).isTrue();
        assertThat(jwtService.validateAccessToken(token)).isFalse();
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractEmail(token)).isEqualTo("test.unit@resolveai.internal");
        assertThat(jwtService.extractTokenType(token)).isEqualTo(JwtService.TOKEN_TYPE_REFRESH);
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("Tampered token fails validation")
    void testTamperedTokenFailsValidation() {
        String token = jwtService.generateAccessToken(testUser);
        String tampered = token.substring(0, token.length() - 5) + "abcde";

        assertThat(jwtService.validateAccessToken(tampered)).isFalse();
        assertThat(jwtService.validateRefreshToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("Expired token fails validation")
    void testExpiredTokenFailsValidation() {
        String expiredToken = jwtService.generateAccessToken(testUser, -1000L);

        assertThat(jwtService.validateAccessToken(expiredToken)).isFalse();
        assertThat(jwtService.isTokenExpired(expiredToken)).isTrue();
    }
}
