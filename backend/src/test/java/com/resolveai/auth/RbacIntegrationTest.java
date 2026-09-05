package com.resolveai.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.auth.dto.RegisterRequest;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.auth.security.AuthorizationService;
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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test suite for Phase 4 Role-Based Access Control (RBAC).
 * Verifies role-based method security, endpoint authorization, 401 vs 403 status distinction,
 * and safeguards against unauthorized privilege escalation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RbacIntegrationTest {

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

    @Autowired
    private AuthorizationService authorizationService;

    private Role employeeRole;
    private Role engineerRole;
    private Role managerRole;
    private Role adminRole;

    private User employeeUser;
    private User engineerUser;
    private User managerUser;
    private User adminUser;

    private String employeeToken;
    private String engineerToken;
    private String managerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        engineerRole = roleRepository.findByName("ENGINEER").orElseThrow();
        managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        employeeUser = createTestUser("emp.rbac@resolveai.internal", employeeRole);
        engineerUser = createTestUser("eng.rbac@resolveai.internal", engineerRole);
        managerUser = createTestUser("mgr.rbac@resolveai.internal", managerRole);
        adminUser = createTestUser("adm.rbac@resolveai.internal", adminRole);

        employeeToken = jwtService.generateAccessToken(employeeUser);
        engineerToken = jwtService.generateAccessToken(engineerUser);
        managerToken = jwtService.generateAccessToken(managerUser);
        adminToken = jwtService.generateAccessToken(adminUser);
    }

    private User createTestUser(String email, Role role) {
        User user = User.builder()
                .email(email.toLowerCase().trim())
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("RBAC")
                .lastName(role.getName())
                .role(role)
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    // =========================================================================
    // 1. UNAUTHENTICATED ACCESS CHECKS (401 Unauthorized)
    // =========================================================================
    @Nested
    @DisplayName("1. Unauthenticated Access Checks")
    class UnauthenticatedChecks {

        @Test
        @DisplayName("1. Unauthenticated request to protected endpoint yields 401 Unauthorized")
        void testUnauthenticatedAccessYields401() throws Exception {
            mockMvc.perform(get("/api/rbac/employee"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

            mockMvc.perform(get("/api/rbac/engineer"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));

            mockMvc.perform(get("/api/rbac/manager"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));

            mockMvc.perform(get("/api/rbac/admin"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("16. Invalid JWT token remains 401 Unauthorized")
        void testInvalidJwtYields401() throws Exception {
            mockMvc.perform(get("/api/rbac/employee")
                            .header("Authorization", "Bearer invalid.token.payload"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("17. Expired JWT token remains 401 Unauthorized")
        void testExpiredJwtYields401() throws Exception {
            String expiredToken = jwtService.generateAccessToken(employeeUser, -5000L);

            mockMvc.perform(get("/api/rbac/employee")
                            .header("Authorization", "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }

    // =========================================================================
    // 2. EMPLOYEE ROLE CHECKS
    // =========================================================================
    @Nested
    @DisplayName("2. EMPLOYEE Role Authorization Checks")
    class EmployeeRoleChecks {

        @Test
        @DisplayName("2. EMPLOYEE can access employee endpoint (200 OK)")
        void testEmployeeCanAccessEmployeeEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/employee")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.resource").value("employee"))
                    .andExpect(jsonPath("$.data.accessGranted").value(true));
        }

        @Test
        @DisplayName("3. EMPLOYEE cannot access engineer endpoint (403 Forbidden)")
        void testEmployeeCannotAccessEngineerEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/engineer")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("4. EMPLOYEE cannot access manager endpoint (403 Forbidden)")
        void testEmployeeCannotAccessManagerEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/manager")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("5. EMPLOYEE cannot access admin endpoint (403 Forbidden)")
        void testEmployeeCannotAccessAdminEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/admin")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 3. ENGINEER ROLE CHECKS
    // =========================================================================
    @Nested
    @DisplayName("3. ENGINEER Role Authorization Checks")
    class EngineerRoleChecks {

        @Test
        @DisplayName("6. ENGINEER can access engineer endpoint (200 OK)")
        void testEngineerCanAccessEngineerEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/engineer")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.resource").value("engineer"))
                    .andExpect(jsonPath("$.data.accessGranted").value(true));
        }

        @Test
        @DisplayName("7. ENGINEER cannot access manager endpoint (403 Forbidden)")
        void testEngineerCannotAccessManagerEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/manager")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("8. ENGINEER cannot access admin endpoint (403 Forbidden)")
        void testEngineerCannotAccessAdminEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/admin")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("ENGINEER cannot access employee-exclusive endpoint (403 Forbidden)")
        void testEngineerCannotAccessEmployeeEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/employee")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 4. MANAGER ROLE CHECKS
    // =========================================================================
    @Nested
    @DisplayName("4. MANAGER Role Authorization Checks")
    class ManagerRoleChecks {

        @Test
        @DisplayName("9. MANAGER can access manager endpoint (200 OK)")
        void testManagerCanAccessManagerEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/manager")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.resource").value("manager"))
                    .andExpect(jsonPath("$.data.accessGranted").value(true));
        }

        @Test
        @DisplayName("10. MANAGER cannot access admin endpoint (403 Forbidden)")
        void testManagerCannotAccessAdminEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/admin")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("MANAGER cannot access engineer-exclusive endpoint (403 Forbidden)")
        void testManagerCannotAccessEngineerEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/engineer")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("MANAGER cannot access employee-exclusive endpoint (403 Forbidden)")
        void testManagerCannotAccessEmployeeEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/employee")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 5. ADMIN ROLE CHECKS
    // =========================================================================
    @Nested
    @DisplayName("5. ADMIN Role Authorization Checks")
    class AdminRoleChecks {

        @Test
        @DisplayName("11. ADMIN can access admin endpoint (200 OK)")
        void testAdminCanAccessAdminEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/admin")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.resource").value("admin"))
                    .andExpect(jsonPath("$.data.accessGranted").value(true));
        }

        @Test
        @DisplayName("ADMIN can access employee endpoint (200 OK)")
        void testAdminCanAccessEmployeeEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/employee")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("ADMIN can access engineer endpoint (200 OK)")
        void testAdminCanAccessEngineerEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/engineer")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("ADMIN can access manager endpoint (200 OK)")
        void testAdminCanAccessManagerEndpoint() throws Exception {
            mockMvc.perform(get("/api/rbac/manager")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }
    }

    // =========================================================================
    // 6. FORBIDDEN ERROR STRUCTURE CHECK (403 Forbidden)
    // =========================================================================
    @Nested
    @DisplayName("6. Forbidden Error Structure Checks")
    class ForbiddenErrorStructureChecks {

        @Test
        @DisplayName("12. Authenticated user with wrong role receives 403 Forbidden with standard ApiErrorResponse")
        void testForbiddenResponseEnvelope() throws Exception {
            mockMvc.perform(get("/api/rbac/admin")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.path").value("/api/rbac/admin"));
        }
    }

    // =========================================================================
    // 7. CURRENT USER AND REGISTRATION SAFEGUARDS
    // =========================================================================
    @Nested
    @DisplayName("7. Profile and Registration Safeguards")
    class ProfileAndRegistrationSafeguards {

        @Test
        @DisplayName("13. /api/users/me still works and reflects correct role for all roles")
        void testCurrentUserReflectsRole() throws Exception {
            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("EMPLOYEE"));

            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("ENGINEER"));

            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("MANAGER"));

            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("ADMIN"));
        }

        @Test
        @DisplayName("14. Public registration still creates EMPLOYEE by default")
        void testRegistrationDefaultsToEmployee() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("default.rbac.user@resolveai.internal")
                    .password("Password123!")
                    .firstName("Default")
                    .lastName("Employee")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.role").value("EMPLOYEE"));

            User user = userRepository.findByEmail("default.rbac.user@resolveai.internal").orElseThrow();
            assertThat(user.getRole().getName()).isEqualTo("EMPLOYEE");
        }

        @Test
        @DisplayName("15. Registration cannot assign ADMIN or privileged roles through request payloads")
        void testRegistrationCannotAssignPrivilegedRoles() throws Exception {
            // Simulated client sending role in payload
            String rawPayload = """
                    {
                        "email": "hacker.admin@resolveai.internal",
                        "password": "Password123!",
                        "firstName": "Hacker",
                        "lastName": "Admin",
                        "role": "ADMIN"
                    }
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawPayload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.role").value("EMPLOYEE"));

            User user = userRepository.findByEmail("hacker.admin@resolveai.internal").orElseThrow();
            assertThat(user.getRole().getName()).isEqualTo("EMPLOYEE");
        }
    }
}
