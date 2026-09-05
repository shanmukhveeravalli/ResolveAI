package com.resolveai.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.auth.dto.LoginRequest;
import com.resolveai.auth.dto.RefreshTokenRequest;
import com.resolveai.auth.dto.RegisterRequest;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.auth.security.JwtService;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive integration test suite verifying Phase 3 Authentication & JWT Security requirements.
 * Covers all 22 required verification test cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Role employeeRole;

    @BeforeEach
    void setUp() {
        employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
    }

    private User createTestUser(String email, String rawPassword) {
        User user = User.builder()
                .email(email.toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .firstName("Test")
                .lastName("User")
                .role(employeeRole)
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    // =========================================================================
    // 1. REGISTRATION TESTS (Test Cases 1-7)
    // =========================================================================
    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("1. Successful registration returns 201 Created and safe DTO")
        void testSuccessfulRegistration() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("john.doe@resolveai.internal")
                    .password("SecurePass123!")
                    .firstName("John")
                    .lastName("Doe")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.email").value("john.doe@resolveai.internal"))
                    .andExpect(jsonPath("$.data.firstName").value("John"))
                    .andExpect(jsonPath("$.data.lastName").value("Doe"))
                    .andExpect(jsonPath("$.data.role").value("EMPLOYEE"));

            assertThat(userRepository.findByEmail("john.doe@resolveai.internal")).isPresent();
        }

        @Test
        @DisplayName("2. Duplicate email registration is rejected with 409 Conflict")
        void testDuplicateEmailRejected() throws Exception {
            createTestUser("existing.user@resolveai.internal", "Password123!");

            RegisterRequest request = RegisterRequest.builder()
                    .email("existing.user@resolveai.internal")
                    .password("NewPassword456!")
                    .firstName("Duplicate")
                    .lastName("User")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("3. Invalid email format is rejected with 400 Bad Request")
        void testInvalidEmailRejected() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("not-a-valid-email-address")
                    .password("ValidPass123!")
                    .firstName("Invalid")
                    .lastName("Email")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("4. Invalid password (< 8 chars) is rejected with 400 Bad Request")
        void testInvalidPasswordRejected() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("short.pass@resolveai.internal")
                    .password("short")
                    .firstName("Short")
                    .lastName("Pass")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("5. Password is stored as BCrypt hash in database and matches raw password")
        void testPasswordStoredHashed() throws Exception {
            String rawPassword = "HashVerificationPassword123!";
            RegisterRequest request = RegisterRequest.builder()
                    .email("hash.check@resolveai.internal")
                    .password(rawPassword)
                    .firstName("Hash")
                    .lastName("Check")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            User user = userRepository.findByEmail("hash.check@resolveai.internal").orElseThrow();
            assertThat(user.getPasswordHash()).isNotEqualTo(rawPassword);
            assertThat(passwordEncoder.matches(rawPassword, user.getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("6. Default assigned role is EMPLOYEE upon registration")
        void testDefaultRoleIsEmployee() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("default.role@resolveai.internal")
                    .password("RolePassword123!")
                    .firstName("Default")
                    .lastName("Role")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.role").value("EMPLOYEE"));

            User user = userRepository.findByEmail("default.role@resolveai.internal").orElseThrow();
            assertThat(user.getRole().getName()).isEqualTo("EMPLOYEE");
        }

        @Test
        @DisplayName("7. Password hash is never returned in registration response")
        void testPasswordHashNotReturned() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("nohash.leak@resolveai.internal")
                    .password("NoLeakPassword123!")
                    .firstName("No")
                    .lastName("Leak")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.data.password_hash").doesNotExist());
        }
    }

    // =========================================================================
    // 2. LOGIN TESTS (Test Cases 8-12)
    // =========================================================================
    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("8. Successful login returns 200 OK with tokens and user summary")
        void testSuccessfulLogin() throws Exception {
            createTestUser("login.success@resolveai.internal", "LoginSuccess123!");

            LoginRequest request = LoginRequest.builder()
                    .email("login.success@resolveai.internal")
                    .password("LoginSuccess123!")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.expiresIn").value(900))
                    .andExpect(jsonPath("$.data.user.email").value("login.success@resolveai.internal"));
        }

        @Test
        @DisplayName("9. Incorrect password is rejected with 401 Unauthorized")
        void testIncorrectPasswordRejected() throws Exception {
            createTestUser("bad.pass@resolveai.internal", "CorrectPass123!");

            LoginRequest request = LoginRequest.builder()
                    .email("bad.pass@resolveai.internal")
                    .password("WrongPassword456!")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        @Test
        @DisplayName("10. Unknown email is rejected with 401 Unauthorized")
        void testUnknownEmailRejected() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("unknown.user@resolveai.internal")
                    .password("AnyPassword123!")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("11. Access token is returned and properly signed")
        void testAccessTokenReturned() throws Exception {
            createTestUser("access.token@resolveai.internal", "ValidPass123!");

            LoginRequest request = LoginRequest.builder()
                    .email("access.token@resolveai.internal")
                    .password("ValidPass123!")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            String accessToken = root.path("data").path("accessToken").asText();

            assertThat(accessToken).isNotBlank();
            assertThat(jwtService.validateAccessToken(accessToken)).isTrue();
            assertThat(jwtService.extractEmail(accessToken)).isEqualTo("access.token@resolveai.internal");
        }

        @Test
        @DisplayName("12. Refresh token is returned and properly signed")
        void testRefreshTokenReturned() throws Exception {
            createTestUser("refresh.token@resolveai.internal", "ValidPass123!");

            LoginRequest request = LoginRequest.builder()
                    .email("refresh.token@resolveai.internal")
                    .password("ValidPass123!")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            String refreshToken = root.path("data").path("refreshToken").asText();

            assertThat(refreshToken).isNotBlank();
            assertThat(jwtService.validateRefreshToken(refreshToken)).isTrue();
            assertThat(jwtService.extractTokenType(refreshToken)).isEqualTo("REFRESH");
        }
    }

    // =========================================================================
    // 3. JWT & FILTER TESTS (Test Cases 13-16)
    // =========================================================================
    @Nested
    @DisplayName("JWT Access & Security Filter Tests")
    class JwtFilterTests {

        @Test
        @DisplayName("13. Valid JWT accesses protected endpoint successfully")
        void testValidJwtAccessesProtectedEndpoint() throws Exception {
            User user = createTestUser("protected.user@resolveai.internal", "ProtectedPass123!");
            String token = jwtService.generateAccessToken(user);

            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.email").value("protected.user@resolveai.internal"));
        }

        @Test
        @DisplayName("14. Missing JWT on protected endpoint is rejected with 401")
        void testMissingJwtRejected() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("15. Invalid / malformed JWT is rejected with 401")
        void testInvalidJwtRejected() throws Exception {
            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer invalid.malformed.jwt.token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("16. Expired JWT is rejected with 401")
        void testExpiredJwtRejected() throws Exception {
            User user = createTestUser("expired.user@resolveai.internal", "ExpiredPass123!");
            // Expired 5 seconds ago
            String expiredToken = jwtService.generateAccessToken(user, -5000L);

            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }

    // =========================================================================
    // 4. REFRESH TOKEN TESTS (Test Cases 17-19)
    // =========================================================================
    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("17. Valid refresh token works and generates new access token")
        void testValidRefreshTokenWorks() throws Exception {
            User user = createTestUser("refresh.valid@resolveai.internal", "RefreshPass123!");
            String refreshToken = jwtService.generateRefreshToken(user);

            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken(refreshToken)
                    .build();

            MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.user.email").value("refresh.valid@resolveai.internal"))
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            String newAccessToken = root.path("data").path("accessToken").asText();

            assertThat(jwtService.validateAccessToken(newAccessToken)).isTrue();
            assertThat(jwtService.extractEmail(newAccessToken)).isEqualTo("refresh.valid@resolveai.internal");
        }

        @Test
        @DisplayName("18. Invalid refresh token is rejected with 401")
        void testInvalidRefreshTokenRejected() throws Exception {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("tampered.or.invalid.refresh.token")
                    .build();

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("19. Expired refresh token is rejected with 401")
        void testExpiredRefreshTokenRejected() throws Exception {
            User user = createTestUser("refresh.expired@resolveai.internal", "ExpiredPass123!");
            // Refresh token expired 5 seconds ago
            String expiredRefreshToken = jwtService.generateRefreshToken(user, -5000L);

            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken(expiredRefreshToken)
                    .build();

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
        }
    }

    // =========================================================================
    // 5. CURRENT USER TESTS (Test Cases 20-22)
    // =========================================================================
    @Nested
    @DisplayName("Current User Endpoint Tests")
    class CurrentUserTests {

        @Test
        @DisplayName("20. Authenticated user can access /api/users/me")
        void testAuthenticatedUserCanAccessMe() throws Exception {
            User user = createTestUser("me.endpoint@resolveai.internal", "MePassword123!");
            String token = jwtService.generateAccessToken(user);

            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.id").value(user.getId()))
                    .andExpect(jsonPath("$.data.email").value("me.endpoint@resolveai.internal"))
                    .andExpect(jsonPath("$.data.firstName").value("Test"))
                    .andExpect(jsonPath("$.data.lastName").value("User"))
                    .andExpect(jsonPath("$.data.role").value("EMPLOYEE"));
        }

        @Test
        @DisplayName("21. Unauthenticated user cannot access /api/users/me")
        void testUnauthenticatedUserCannotAccessMe() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("22. Password hash is never exposed on /api/users/me")
        void testPasswordHashNotExposedOnMe() throws Exception {
            User user = createTestUser("no.hash.me@resolveai.internal", "NoHashPass123!");
            String token = jwtService.generateAccessToken(user);

            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.data.password_hash").doesNotExist());
        }
    }
}
