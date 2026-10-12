package com.resolveai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.auth.dto.LoginRequest;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.security.JwtService;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase17SecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("Security Headers Validation")
    class SecurityHeadersTests {

        @Test
        @DisplayName("Responses must include standard security headers (X-Frame-Options, X-Content-Type-Options, Referrer-Policy)")
        void responseContainsSecurityHeaders() throws Exception {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
        }
    }

    @Nested
    @DisplayName("JWT Security & Token Type Strictness")
    class JwtSecurityTests {

        @Test
        @DisplayName("Access token cannot be used at /api/auth/refresh (must be REFRESH type)")
        void accessTokenCannotBeUsedAsRefreshToken() throws Exception {
            User user = userRepository.findByEmail("admin@resolveai.com").orElseGet(() -> {
                Role adminRole = Role.builder().id(4L).name("ADMIN").build();
                return User.builder().id(999L).email("admin@resolveai.com").role(adminRole).passwordHash("hash").build();
            });

            String accessToken = jwtService.generateAccessToken(user);

            String requestBody = "{\"refreshToken\": \"" + accessToken + "\"}";

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("INVALID_TOKEN"))
                    .andExpect(jsonPath("$.message").value(containsString("Invalid or expired refresh token")));
        }

        @Test
        @DisplayName("Refresh token cannot be used to authenticate protected endpoints (must be ACCESS type)")
        void refreshTokenCannotAccessProtectedEndpoints() throws Exception {
            User user = userRepository.findByEmail("admin@resolveai.com").orElseGet(() -> {
                Role adminRole = Role.builder().id(4L).name("ADMIN").build();
                return User.builder().id(999L).email("admin@resolveai.com").role(adminRole).passwordHash("hash").build();
            });

            String refreshToken = jwtService.generateRefreshToken(user);

            mockMvc.perform(get("/api/incidents")
                            .header("Authorization", "Bearer " + refreshToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("Rate Limiting Response Headers")
    class RateLimitingHeadersTests {

        @Test
        @DisplayName("Rate-limited endpoints expose X-RateLimit headers on responses")
        void rateLimitedEndpointExposesHeaders() throws Exception {
            LoginRequest req = new LoginRequest("nonexistent@example.com", "wrongpassword123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().string("X-RateLimit-Limit", "15"));
        }
    }

    @Nested
    @DisplayName("Actuator Endpoint Role Restrictions")
    class ActuatorSecurityTests {

        @Test
        @DisplayName("Health endpoint is publicly accessible")
        void actuatorHealthIsPublic() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Secured actuator endpoints (e.g. /actuator/beans) require authentication and ADMIN role")
        void actuatorBeansRequiresAdmin() throws Exception {
            mockMvc.perform(get("/actuator/beans"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
