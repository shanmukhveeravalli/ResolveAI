package com.resolveai;

import com.resolveai.audit.entity.AuditLog;
import com.resolveai.audit.repository.AuditLogRepository;
import com.resolveai.auth.entity.Role;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.incident.entity.*;
import com.resolveai.incident.repository.*;
import com.resolveai.knowledge.entity.IncidentKnowledgeLink;
import com.resolveai.knowledge.entity.KnowledgeArticle;
import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import com.resolveai.knowledge.repository.IncidentKnowledgeLinkRepository;
import com.resolveai.knowledge.repository.KnowledgeArticleRepository;
import com.resolveai.notification.entity.Notification;
import com.resolveai.notification.repository.NotificationRepository;
import com.resolveai.sla.entity.SlaPolicy;
import com.resolveai.sla.entity.SlaRecord;
import com.resolveai.sla.repository.SlaPolicyRepository;
import com.resolveai.sla.repository.SlaRecordRepository;
import com.resolveai.team.entity.Team;
import com.resolveai.team.entity.TeamMember;
import com.resolveai.team.repository.TeamMemberRepository;
import com.resolveai.team.repository.TeamRepository;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepositoryIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentCommentRepository incidentCommentRepository;

    @Autowired
    private IncidentHistoryRepository incidentHistoryRepository;

    @Autowired
    private SlaPolicyRepository slaPolicyRepository;

    @Autowired
    private SlaRecordRepository slaRecordRepository;

    @Autowired
    private KnowledgeArticleRepository knowledgeArticleRepository;

    @Autowired
    private IncidentKnowledgeLinkRepository incidentKnowledgeLinkRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Test
    @DisplayName("Verify persistence of core entity lifecycle: User, Team, Incident, SLA, Comment, and History")
    void testCorePersistenceLifecycle() {
        // 1. Roles
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Role engineerRole = roleRepository.findByName("ENGINEER").orElseThrow();

        // 2. Users
        User reporter = User.builder()
                .email("alice.reporter@resolveai.internal")
                .passwordHash("$2a$12$e8x...")
                .firstName("Alice")
                .lastName("Smith")
                .role(employeeRole)
                .isActive(true)
                .build();
        reporter = userRepository.save(reporter);
        assertThat(reporter.getId()).isNotNull();

        User engineer = User.builder()
                .email("bob.engineer@resolveai.internal")
                .passwordHash("$2a$12$e8x...")
                .firstName("Bob")
                .lastName("Vance")
                .role(engineerRole)
                .isActive(true)
                .build();
        engineer = userRepository.save(engineer);

        // 3. Team
        Team networkTeam = Team.builder()
                .name("Network Operations")
                .description("Handles office network and VPN routing")
                .leadUser(engineer)
                .isActive(true)
                .build();
        networkTeam = teamRepository.save(networkTeam);
        assertThat(networkTeam.getId()).isNotNull();

        // 4. Team Member
        TeamMember member = TeamMember.builder()
                .team(networkTeam)
                .user(engineer)
                .build();
        teamMemberRepository.save(member);
        assertThat(teamMemberRepository.existsByTeamIdAndUserId(networkTeam.getId(), engineer.getId())).isTrue();

        // 5. Category
        Category category = categoryRepository.findBySlug("network-connectivity").orElseThrow();

        // 6. Incident
        Incident incident = Incident.builder()
                .incidentNumber("INC-20260905-0001")
                .title("VPN Gateway timeout on East Hub")
                .description("Users experiencing 504 gateway timeout connecting to remote office VPN")
                .status(IncidentStatus.NEW)
                .priority(Priority.P2)
                .severity(Severity.HIGH)
                .category(category)
                .reporter(reporter)
                .assignee(engineer)
                .team(networkTeam)
                .build();
        incident = incidentRepository.save(incident);
        assertThat(incident.getId()).isNotNull();
        assertThat(incidentRepository.findByIncidentNumber("INC-20260905-0001")).isPresent();

        // 7. SLA Record (1:1 with Incident)
        SlaPolicy p2Policy = slaPolicyRepository.findByPriority(Priority.P2).orElseThrow();
        Instant now = Instant.now();
        SlaRecord slaRecord = SlaRecord.builder()
                .incident(incident)
                .slaPolicy(p2Policy)
                .responseDueAt(now.plus(30, ChronoUnit.MINUTES))
                .resolutionDueAt(now.plus(480, ChronoUnit.MINUTES))
                .isResponseBreached(false)
                .isResolutionBreached(false)
                .build();
        slaRecord = slaRecordRepository.save(slaRecord);
        assertThat(slaRecord.getId()).isNotNull();
        assertThat(slaRecordRepository.findByIncidentId(incident.getId())).isPresent();

        // 8. Incident Comment
        IncidentComment comment = IncidentComment.builder()
                .incident(incident)
                .author(engineer)
                .commentText("Investigating routing table on edge gateway router.")
                .isInternal(true)
                .build();
        comment = incidentCommentRepository.save(comment);
        assertThat(comment.getId()).isNotNull();
        assertThat(incidentCommentRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId())).hasSize(1);

        // 9. Incident History
        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actor(engineer)
                .actionType("STATUS_CHANGE")
                .fieldName("status")
                .oldValue("NEW")
                .newValue("IN_PROGRESS")
                .build();
        history = incidentHistoryRepository.save(history);
        assertThat(history.getId()).isNotNull();
        assertThat(incidentHistoryRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).hasSize(1);

        // 10. Knowledge Article & Link
        KnowledgeArticle article = KnowledgeArticle.builder()
                .title("VPN Gateway BGP Reset SOP")
                .slug("vpn-gateway-bgp-reset-sop")
                .problem("VPN client connection stalls at 80%")
                .symptoms("BGP table desync in edge logs")
                .rootCause("Route table flap during morning synchronization")
                .resolution("Execute soft clear on BGP neighbor session")
                .category(category)
                .author(engineer)
                .status(KnowledgeArticleStatus.PUBLISHED)
                .tags("vpn,network,bgp")
                .build();
        article = knowledgeArticleRepository.save(article);
        assertThat(article.getId()).isNotNull();

        IncidentKnowledgeLink link = IncidentKnowledgeLink.builder()
                .incident(incident)
                .article(article)
                .relevanceScore(BigDecimal.valueOf(0.9500))
                .linkedByAi(false)
                .build();
        link = incidentKnowledgeLinkRepository.save(link);
        assertThat(link.getId()).isNotNull();
        assertThat(incidentKnowledgeLinkRepository.existsByIncidentIdAndArticleId(incident.getId(), article.getId())).isTrue();

        // 11. Notification
        Notification notification = Notification.builder()
                .user(reporter)
                .title("Incident Assigned")
                .message("Your incident INC-20260905-0001 has been assigned to Bob Vance.")
                .type("INCIDENT_ASSIGNED")
                .referenceId(incident.getId())
                .isRead(false)
                .build();
        notification = notificationRepository.save(notification);
        assertThat(notification.getId()).isNotNull();
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(reporter.getId())).isEqualTo(1);

        // 12. Audit Log
        AuditLog auditLog = AuditLog.builder()
                .user(reporter)
                .action("CREATE_INCIDENT")
                .resourceType("INCIDENT")
                .resourceId(incident.getId())
                .clientIp("127.0.0.1")
                .userAgent("Mozilla/5.0")
                .details("{\"incidentNumber\":\"INC-20260905-0001\"}")
                .build();
        auditLog = auditLogRepository.save(auditLog);
        assertThat(auditLog.getId()).isNotNull();

        // 13. Attachment
        Attachment attachment = Attachment.builder()
                .incident(incident)
                .comment(comment)
                .fileName("gateway_logs.txt")
                .fileType("text/plain")
                .fileSizeBytes(1024L)
                .storagePath("/storage/attachments/2026/09/gateway_logs.txt")
                .uploadedBy(engineer)
                .build();
        attachment = attachmentRepository.save(attachment);
        assertThat(attachment.getId()).isNotNull();
        assertThat(attachmentRepository.findByIncidentId(incident.getId())).hasSize(1);
    }
}
