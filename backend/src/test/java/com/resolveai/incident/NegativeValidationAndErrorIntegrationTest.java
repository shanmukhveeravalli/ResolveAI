package com.resolveai.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.auth.security.JwtService;
import com.resolveai.incident.dto.IncidentCreateRequest;
import com.resolveai.incident.dto.IncidentStatusUpdateRequest;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentStatus;
import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.entity.Severity;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Negative Validation & RFC-7807 Error Envelope Tests (Phase 13)")
class NegativeValidationAndErrorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User employeeUser;
    private User otherEmployeeUser;
    private String employeeToken;
    private String otherEmployeeToken;

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();

        employeeUser = userRepository.save(User.builder()
                .email("test.employee1@resolveai.com")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .firstName("Alice")
                .lastName("Emp")
                .role(employeeRole)
                .isActive(true)
                .build());

        otherEmployeeUser = userRepository.save(User.builder()
                .email("test.employee2@resolveai.com")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .firstName("Bob")
                .lastName("Emp")
                .role(employeeRole)
                .isActive(true)
                .build());

        employeeToken = jwtService.generateAccessToken(employeeUser);
        otherEmployeeToken = jwtService.generateAccessToken(otherEmployeeUser);
    }

    @Test
    @DisplayName("401 UNAUTHORIZED: Request to protected endpoint without Bearer token returns RFC envelope")
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("400 BAD REQUEST: Request with blank title and missing priority returns field validation error")
    void validationErrorReturns400() throws Exception {
        IncidentCreateRequest badRequest = new IncidentCreateRequest();
        badRequest.setTitle(""); // blank -> triggers @NotBlank
        badRequest.setDescription("Valid description");

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("404 NOT FOUND: Request for non-existent incident ID returns ResourceNotFoundException")
    void nonExistentIncidentReturns404() throws Exception {
        mockMvc.perform(get("/api/incidents/999999")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("403 FORBIDDEN: Employee attempting to view another employee's incident returns AccessDenied")
    void unauthorizedIncidentAccessReturns403() throws Exception {
        Incident foreignIncident = incidentRepository.save(Incident.builder()
                .incidentNumber("INC-TEST-403")
                .title("Foreign Ticket")
                .description("Private ticket")
                .status(IncidentStatus.NEW)
                .priority(Priority.P2)
                .severity(Severity.MEDIUM)
                .reporter(otherEmployeeUser)
                .build());

        // Employee 1 attempts to access Employee 2's incident
        mockMvc.perform(get("/api/incidents/" + foreignIncident.getId())
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("400 BAD REQUEST: Invalid status transition (NEW -> CLOSED) is rejected with clear error")
    void invalidStatusTransitionReturns400() throws Exception {
        Incident incident = incidentRepository.save(Incident.builder()
                .incidentNumber("INC-TEST-INVALID-TRANS")
                .title("Transition Test Ticket")
                .description("Testing illegal transition")
                .status(IncidentStatus.NEW)
                .priority(Priority.P2)
                .severity(Severity.MEDIUM)
                .reporter(employeeUser)
                .build());

        IncidentStatusUpdateRequest statusUpdate = new IncidentStatusUpdateRequest();
        statusUpdate.setStatus(IncidentStatus.CLOSED);

        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusUpdate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
    }
}