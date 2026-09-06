package com.resolveai.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.auth.security.JwtService;
import com.resolveai.incident.dto.IncidentAssignRequest;
import com.resolveai.incident.dto.IncidentCommentCreateRequest;
import com.resolveai.incident.dto.IncidentCreateRequest;
import com.resolveai.incident.dto.IncidentStatusUpdateRequest;
import com.resolveai.incident.entity.Category;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentStatus;
import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.entity.Severity;
import com.resolveai.incident.repository.CategoryRepository;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.incident.service.IncidentCommentService;
import com.resolveai.incident.service.IncidentService;
import com.resolveai.notification.entity.Notification;
import com.resolveai.notification.entity.NotificationType;
import com.resolveai.notification.repository.NotificationRepository;
import com.resolveai.notification.service.NotificationService;
import com.resolveai.sla.service.SlaService;
import com.resolveai.team.dto.TeamMemberRequest;
import com.resolveai.team.entity.Team;
import com.resolveai.team.repository.TeamRepository;
import com.resolveai.team.service.TeamService;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.resolveai.auth.security.SecurityUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive integration tests for Phase 7 Notifications module.
 * Verifies notification persistence, retrieval, pagination, unread counts,
 * marking as read, marking all read, incident/SLA/team event notifications,
 * and security rules (401, 403, 404).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private IncidentCommentService incidentCommentService;

    @Autowired
    private SlaService slaService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Role employeeRole;
    private Role engineerRole;
    private Role managerRole;
    private Role adminRole;

    private User employeeUser;
    private User otherEmployeeUser;
    private User engineerUser;
    private User managerUser;
    private User adminUser;

    private String employeeToken;
    private String otherEmployeeToken;
    private String engineerToken;
    private String managerToken;
    private String adminToken;

    private Category testCategory;
    private Team testTeam;

    @BeforeEach
    void setUp() {
        employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        engineerRole = roleRepository.findByName("ENGINEER").orElseThrow();
        managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        testTeam = teamRepository.save(Team.builder()
                .name("Notification Ops Squad")
                .description("Squad for notification testing")
                .isActive(true)
                .build());

        employeeUser = createTestUser("notif.emp@enterprise.com", employeeRole, null);
        otherEmployeeUser = createTestUser("notif.other@enterprise.com", employeeRole, null);
        engineerUser = createTestUser("notif.eng@enterprise.com", engineerRole, testTeam);
        managerUser = createTestUser("notif.mgr@enterprise.com", managerRole, testTeam);
        adminUser = createTestUser("notif.adm@enterprise.com", adminRole, null);

        employeeToken = jwtService.generateAccessToken(employeeUser);
        otherEmployeeToken = jwtService.generateAccessToken(otherEmployeeUser);
        engineerToken = jwtService.generateAccessToken(engineerUser);
        managerToken = jwtService.generateAccessToken(managerUser);
        adminToken = jwtService.generateAccessToken(adminUser);

        testCategory = categoryRepository.save(Category.builder()
                .name("Notification Test Category")
                .slug("notif-test-cat-" + System.currentTimeMillis())
                .description("Category for testing notifications")
                .isActive(true)
                .build());
    }

    private User createTestUser(String email, Role role, Team team) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .firstName("Test")
                .lastName(role.getName())
                .role(role)
                .team(team)
                .isActive(true)
                .build()));
    }

    private void authenticate(User user) {
        SecurityUser securityUser = new SecurityUser(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("Notification Creation and Persistence Tests")
    class CreationTests {

        @Test
        @DisplayName("Should persist notification with correct fields")
        void testCreateAndPersistNotification() {
            Notification notification = notificationService.createNotification(
                    employeeUser,
                    NotificationType.INCIDENT_CREATED,
                    "Ticket Created",
                    "Ticket INC-001 has been registered.",
                    101L
            );

            assertThat(notification).isNotNull();
            assertThat(notification.getId()).isNotNull();
            assertThat(notification.getUser().getId()).isEqualTo(employeeUser.getId());
            assertThat(notification.getType()).isEqualTo("INCIDENT_CREATED");
            assertThat(notification.getTitle()).isEqualTo("Ticket Created");
            assertThat(notification.getMessage()).isEqualTo("Ticket INC-001 has been registered.");
            assertThat(notification.getReferenceId()).isEqualTo(101L);
            assertThat(notification.getIsRead()).isFalse();
            assertThat(notification.getReadAt()).isNull();
            assertThat(notification.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Notification Retrieval and Pagination Tests")
    class RetrievalTests {

        @Test
        @DisplayName("Authenticated user retrieves their own notifications with pagination")
        void testRetrieveOwnNotifications() throws Exception {
            for (int i = 1; i <= 5; i++) {
                notificationService.createNotification(
                        employeeUser,
                        NotificationType.INCIDENT_ASSIGNED,
                        "Title " + i,
                        "Message " + i,
                        (long) i
                );
            }

            // Create notification for another user that should NOT be visible
            notificationService.createNotification(
                    otherEmployeeUser,
                    NotificationType.INCIDENT_ASSIGNED,
                    "Other User Title",
                    "Other User Message",
                    999L
            );

            mockMvc.perform(get("/api/notifications")
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("page", "0")
                            .param("size", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(3)))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.content[0].type").value("INCIDENT_ASSIGNED"))
                    .andExpect(jsonPath("$.content[0].isRead").value(false));
        }

        @Test
        @DisplayName("User cannot see notifications of another user")
        void testUserIsolation() throws Exception {
            notificationService.createNotification(
                    otherEmployeeUser,
                    NotificationType.INCIDENT_CREATED,
                    "Private Notification",
                    "Confidential text",
                    555L
            );

            mockMvc.perform(get("/api/notifications")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("Unread Count Tests")
    class UnreadCountTests {

        @Test
        @DisplayName("Unread count returns correct count and excludes read notifications")
        void testGetUnreadCount() throws Exception {
            Notification n1 = notificationService.createNotification(
                    employeeUser, NotificationType.INCIDENT_CREATED, "T1", "M1", 1L);
            Notification n2 = notificationService.createNotification(
                    employeeUser, NotificationType.INCIDENT_CREATED, "T2", "M2", 2L);
            Notification n3 = notificationService.createNotification(
                    employeeUser, NotificationType.INCIDENT_CREATED, "T3", "M3", 3L);

            // Create notification for other user
            notificationService.createNotification(
                    otherEmployeeUser, NotificationType.INCIDENT_CREATED, "T4", "M4", 4L);

            // Mark one notification as read
            notificationService.markAsRead(n1.getId(), employeeUser.getId());

            mockMvc.perform(get("/api/notifications/unread-count")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(2));
        }
    }

    @Nested
    @DisplayName("Mark As Read Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("Marking own notification as read updates state and read timestamp")
        void testMarkAsReadSuccess() throws Exception {
            Notification notif = notificationService.createNotification(
                    employeeUser, NotificationType.INCIDENT_ASSIGNED, "Assigned", "You have a task", 10L);

            mockMvc.perform(patch("/api/notifications/" + notif.getId() + "/read")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(notif.getId()))
                    .andExpect(jsonPath("$.isRead").value(true))
                    .andExpect(jsonPath("$.read").value(true))
                    .andExpect(jsonPath("$.readAt").isNotEmpty());

            Notification updated = notificationRepository.findById(notif.getId()).orElseThrow();
            assertThat(updated.getIsRead()).isTrue();
            assertThat(updated.getReadAt()).isNotNull();
        }

        @Test
        @DisplayName("Marking as read is idempotent")
        void testMarkAsReadIdempotent() throws Exception {
            Notification notif = notificationService.createNotification(
                    employeeUser, NotificationType.INCIDENT_ASSIGNED, "Assigned", "You have a task", 10L);

            notificationService.markAsRead(notif.getId(), employeeUser.getId());
            Instant firstReadAt = notificationRepository.findById(notif.getId()).orElseThrow().getReadAt();

            mockMvc.perform(patch("/api/notifications/" + notif.getId() + "/read")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isRead").value(true));

            Instant secondReadAt = notificationRepository.findById(notif.getId()).orElseThrow().getReadAt();
            assertThat(firstReadAt).isEqualTo(secondReadAt);
        }

        @Test
        @DisplayName("Marking another user's notification as read returns 403 Forbidden")
        void testMarkOtherUserNotifForbidden() throws Exception {
            Notification otherNotif = notificationService.createNotification(
                    otherEmployeeUser, NotificationType.INCIDENT_CREATED, "Other", "Private", 20L);

            mockMvc.perform(patch("/api/notifications/" + otherNotif.getId() + "/read")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Marking nonexistent notification returns 404 Not Found")
        void testMarkNonexistentNotFound() throws Exception {
            mockMvc.perform(patch("/api/notifications/999999/read")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Mark All As Read Tests")
    class MarkAllReadTests {

        @Test
        @DisplayName("Mark all as read updates all unread notifications for current user without modifying other users")
        void testMarkAllAsRead() throws Exception {
            notificationService.createNotification(employeeUser, NotificationType.INCIDENT_CREATED, "E1", "M1", 1L);
            notificationService.createNotification(employeeUser, NotificationType.INCIDENT_CREATED, "E2", "M2", 2L);
            notificationService.createNotification(otherEmployeeUser, NotificationType.INCIDENT_CREATED, "O1", "M1", 3L);

            assertThat(notificationRepository.countByUserIdAndIsReadFalse(employeeUser.getId())).isEqualTo(2);
            assertThat(notificationRepository.countByUserIdAndIsReadFalse(otherEmployeeUser.getId())).isEqualTo(1);

            mockMvc.perform(patch("/api/notifications/read-all")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updatedCount").value(2));

            assertThat(notificationRepository.countByUserIdAndIsReadFalse(employeeUser.getId())).isEqualTo(0);
            assertThat(notificationRepository.countByUserIdAndIsReadFalse(otherEmployeeUser.getId())).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Incident Integration Event Tests")
    class IncidentIntegrationTests {

        @Test
        @DisplayName("Incident creation creates INCIDENT_CREATED notification for reporter")
        void testIncidentCreationNotification() {
            authenticate(employeeUser);
            IncidentCreateRequest request = IncidentCreateRequest.builder()
                    .title("Email service degradation")
                    .description("High latency on email delivery")
                    .priority(Priority.P2)
                    .severity(Severity.HIGH)
                    .categoryId(testCategory.getId())
                    .build();

            var response = incidentService.createIncident(request, employeeUser.getEmail());

            List<Notification> notifications = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(employeeUser.getId());
            assertThat(notifications).isNotEmpty();
            Notification creationNotif = notifications.getFirst();
            assertThat(creationNotif.getType()).isEqualTo(NotificationType.INCIDENT_CREATED.name());
            assertThat(creationNotif.getReferenceId()).isEqualTo(response.getId());
        }

        @Test
        @DisplayName("Incident assignment creates INCIDENT_ASSIGNED notification for engineer")
        void testIncidentAssignmentNotification() {
            authenticate(employeeUser);
            IncidentCreateRequest createReq = IncidentCreateRequest.builder()
                    .title("VPN connection failure")
                    .description("Unable to connect to internal network")
                    .priority(Priority.P2)
                    .severity(Severity.HIGH)
                    .categoryId(testCategory.getId())
                    .build();

            var created = incidentService.createIncident(createReq, employeeUser.getEmail());

            authenticate(managerUser);
            IncidentAssignRequest assignReq = IncidentAssignRequest.builder()
                    .engineerId(engineerUser.getId())
                    .assignmentReason("Network team routing")
                    .build();

            incidentService.assignIncident(created.getId(), assignReq, managerUser.getEmail());

            List<Notification> engineerNotifs = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(engineerUser.getId());
            assertThat(engineerNotifs).isNotEmpty();
            Notification assignNotif = engineerNotifs.getFirst();
            assertThat(assignNotif.getType()).isEqualTo(NotificationType.INCIDENT_ASSIGNED.name());
            assertThat(assignNotif.getReferenceId()).isEqualTo(created.getId());
        }

        @Test
        @DisplayName("Status transition creates appropriate status notifications")
        void testStatusTransitionNotifications() {
            Incident incident = incidentRepository.save(Incident.builder()
                    .incidentNumber("INC-TEST-001")
                    .title("Database slow queries")
                    .description("Query response time > 5s")
                    .status(IncidentStatus.ASSIGNED)
                    .priority(Priority.P2)
                    .severity(Severity.HIGH)
                    .category(testCategory)
                    .reporter(employeeUser)
                    .assignee(engineerUser)
                    .team(testTeam)
                    .build());

            // 1. Transition to IN_PROGRESS
            authenticate(engineerUser);
            incidentService.updateStatus(incident.getId(),
                    IncidentStatusUpdateRequest.builder().status(IncidentStatus.IN_PROGRESS).build(),
                    engineerUser.getEmail());

            List<Notification> empNotifs = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(employeeUser.getId());
            assertThat(empNotifs).anyMatch(n -> n.getType().equals(NotificationType.INCIDENT_STATUS_CHANGED.name()));

            // 2. Transition to ESCALATED
            authenticate(engineerUser);
            incidentService.updateStatus(incident.getId(),
                    IncidentStatusUpdateRequest.builder().status(IncidentStatus.ESCALATED).build(),
                    engineerUser.getEmail());

            empNotifs = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(employeeUser.getId());
            assertThat(empNotifs).anyMatch(n -> n.getType().equals(NotificationType.INCIDENT_ESCALATED.name()));

            // 3. Transition to RESOLVED
            authenticate(engineerUser);
            incidentService.updateStatus(incident.getId(),
                    IncidentStatusUpdateRequest.builder().status(IncidentStatus.RESOLVED).build(),
                    engineerUser.getEmail());

            empNotifs = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(employeeUser.getId());
            assertThat(empNotifs).anyMatch(n -> n.getType().equals(NotificationType.INCIDENT_RESOLVED.name()));

            // 4. Transition to REOPENED
            authenticate(employeeUser);
            incidentService.updateStatus(incident.getId(),
                    IncidentStatusUpdateRequest.builder().status(IncidentStatus.REOPENED).build(),
                    employeeUser.getEmail());

            List<Notification> engNotifs = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(engineerUser.getId());
            assertThat(engNotifs).anyMatch(n -> n.getType().equals(NotificationType.INCIDENT_REOPENED.name()));
        }

        @Test
        @DisplayName("Adding incident comment notifies other participants without self-notifying author")
        void testCommentNotification() {
            Incident incident = incidentRepository.save(Incident.builder()
                    .incidentNumber("INC-TEST-002")
                    .title("Storage volume full")
                    .description("Disk usage reached 98%")
                    .status(IncidentStatus.IN_PROGRESS)
                    .priority(Priority.P2)
                    .severity(Severity.HIGH)
                    .category(testCategory)
                    .reporter(employeeUser)
                    .assignee(engineerUser)
                    .team(testTeam)
                    .build());

            // Engineer posts a public comment
            authenticate(engineerUser);
            incidentCommentService.addComment(
                    incident.getId(),
                    IncidentCommentCreateRequest.builder().commentText("Investigating logs now.").isInternal(false).build(),
                    engineerUser.getEmail()
            );

            // Reporter should receive INCIDENT_COMMENT_ADDED
            List<Notification> empNotifs = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(employeeUser.getId());
            assertThat(empNotifs).anyMatch(n -> n.getType().equals(NotificationType.INCIDENT_COMMENT_ADDED.name()));

            // Comment author (engineer) should NOT receive a notification for their own comment
            List<Notification> engNotifs = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(engineerUser.getId());
            assertThat(engNotifs).noneMatch(n -> n.getType().equals(NotificationType.INCIDENT_COMMENT_ADDED.name()));
        }
    }

    @Nested
    @DisplayName("SLA Breach Notification Tests")
    class SlaIntegrationTests {

        @Test
        @DisplayName("SLA breach triggers SLA_BREACHED notification and prevents duplicate notifications")
        void testSlaBreachNotificationAndIdempotency() {
            Incident incident = incidentRepository.save(Incident.builder()
                    .incidentNumber("INC-TEST-SLA")
                    .title("Critical core router failure")
                    .description("Traffic completely halted")
                    .status(IncidentStatus.NEW)
                    .priority(Priority.P1)
                    .severity(Severity.CRITICAL)
                    .category(testCategory)
                    .reporter(employeeUser)
                    .assignee(engineerUser)
                    .team(testTeam)
                    .build());

            slaService.createSlaRecordForIncident(incident);

            // Record response 2 hours late (P1 response SLA is 15 minutes)
            slaService.recordResponse(incident, Instant.now().plus(Duration.ofHours(2)));

            List<Notification> engNotifs = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(engineerUser.getId());
            long slaNotifCount = engNotifs.stream()
                    .filter(n -> n.getType().equals(NotificationType.SLA_BREACHED.name()))
                    .count();
            assertThat(slaNotifCount).isEqualTo(1);

            // Scan for breaches again; duplicate should NOT be created
            slaService.scanAndEvaluateActiveBreaches(Instant.now().plus(Duration.ofDays(1)));

            engNotifs = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(engineerUser.getId());
            long slaNotifCountAfterScan = engNotifs.stream()
                    .filter(n -> n.getType().equals(NotificationType.SLA_BREACHED.name()))
                    .count();
            assertThat(slaNotifCountAfterScan).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Team Assignment Notification Tests")
    class TeamIntegrationTests {

        @Test
        @DisplayName("Adding user to a team generates TEAM_ASSIGNMENT notification")
        void testTeamAssignmentNotification() {
            teamService.addTeamMember(testTeam.getId(), TeamMemberRequest.builder().userId(otherEmployeeUser.getId()).build());

            List<Notification> notifs = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(otherEmployeeUser.getId());
            assertThat(notifs).anyMatch(n -> n.getType().equals(NotificationType.TEAM_ASSIGNMENT.name())
                    && n.getReferenceId().equals(testTeam.getId()));
        }
    }

    @Nested
    @DisplayName("Security & Authentication Tests")
    class SecurityTests {

        @Test
        @DisplayName("Unauthenticated request to /api/notifications returns 401")
        void testUnauthenticatedAccessReturns401() throws Exception {
            mockMvc.perform(get("/api/notifications"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Unauthenticated request to /api/notifications/unread-count returns 401")
        void testUnauthenticatedUnreadCountReturns401() throws Exception {
            mockMvc.perform(get("/api/notifications/unread-count"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Unauthenticated request to /api/notifications/{id}/read returns 401")
        void testUnauthenticatedMarkReadReturns401() throws Exception {
            mockMvc.perform(patch("/api/notifications/1/read"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Unauthenticated request to /api/notifications/read-all returns 401")
        void testUnauthenticatedReadAllReturns401() throws Exception {
            mockMvc.perform(patch("/api/notifications/read-all"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Malformed notification ID parameter returns 400 Bad Request")
        void testMalformedNotificationIdReturns400() throws Exception {
            mockMvc.perform(patch("/api/notifications/invalid-id/read")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isBadRequest());
        }
    }
}
