package com.resolveai.incident.service;

import com.resolveai.auth.security.AuthorizationService;
import com.resolveai.auth.security.RoleConstants;
import com.resolveai.common.dto.PageResponse;
import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.incident.dto.*;
import com.resolveai.incident.entity.*;
import com.resolveai.incident.exception.InvalidIncidentStatusTransitionException;
import com.resolveai.incident.repository.CategoryRepository;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.resolveai.notification.entity.NotificationType;
import com.resolveai.notification.service.NotificationService;
import com.resolveai.sla.entity.SlaRecord;
import com.resolveai.sla.service.SlaService;
import com.resolveai.team.entity.Team;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Core Service handling incident creation, lifecycle management, assignment,
 * resource-level authorization, and search filtering.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = Map.of(
            IncidentStatus.NEW, Set.of(IncidentStatus.TRIAGED, IncidentStatus.ASSIGNED),
            IncidentStatus.TRIAGED, Set.of(IncidentStatus.ASSIGNED),
            IncidentStatus.ASSIGNED, Set.of(IncidentStatus.IN_PROGRESS),
            IncidentStatus.IN_PROGRESS, Set.of(IncidentStatus.RESOLVED, IncidentStatus.ESCALATED),
            IncidentStatus.ESCALATED, Set.of(IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED),
            IncidentStatus.RESOLVED, Set.of(IncidentStatus.CLOSED, IncidentStatus.REOPENED),
            IncidentStatus.REOPENED, Set.of(IncidentStatus.IN_PROGRESS),
            IncidentStatus.CLOSED, Set.of()
    );

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final IncidentRepository incidentRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final IncidentHistoryService incidentHistoryService;
    private final IncidentCommentService incidentCommentService;
    private final AuthorizationService authorizationService;
    private final SlaService slaService;
    private final NotificationService notificationService;

    /**
     * Creates a new incident, assigns the authenticated user as the reporter, and logs creation audit.
     */
    @Transactional
    public IncidentResponse createIncident(IncidentCreateRequest request, String currentUserEmail) {
        User reporter = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserEmail));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));

        if (Boolean.FALSE.equals(category.getIsActive())) {
            throw new IllegalArgumentException("Cannot report an incident against an inactive category");
        }

        String incidentNumber = generateUniqueIncidentNumber();

        Incident incident = Incident.builder()
                .incidentNumber(incidentNumber)
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .status(IncidentStatus.NEW)
                .priority(request.getPriority())
                .severity(request.getSeverity())
                .category(category)
                .reporter(reporter)
                .team(reporter.getTeam())
                .build();

        Incident saved = incidentRepository.save(incident);
        log.info("Incident created with ID {} and number {}", saved.getId(), saved.getIncidentNumber());

        incidentHistoryService.recordHistory(saved, reporter, "CREATED", "incident", null, saved.getIncidentNumber());

        SlaRecord slaRecord = slaService.createSlaRecordForIncident(saved);

        notificationService.createNotification(
                reporter,
                NotificationType.INCIDENT_CREATED,
                "Incident Created: " + saved.getIncidentNumber(),
                "Your incident '" + saved.getTitle() + "' has been created with priority " + saved.getPriority() + ".",
                saved.getId()
        );

        return IncidentResponse.fromEntity(saved, slaRecord);
    }

    /**
     * Retrieves a single incident by ID subject to resource-level authorization.
     */
    @Transactional(readOnly = true)
    public IncidentResponse getIncidentById(Long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));

        if (!authorizationService.canAccessIncident(incident)) {
            throw new AccessDeniedException("Access denied: insufficient permissions to access incident ID: " + id);
        }

        SlaRecord slaRecord = slaService.findSlaRecordByIncidentId(id).orElse(null);
        return IncidentResponse.fromEntity(incident, slaRecord);
    }

    /**
     * Lists incidents with role-based scoping and multi-attribute filters.
     */
    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> getIncidents(IncidentStatus status,
                                                      Priority priority,
                                                      Severity severity,
                                                      Long categoryId,
                                                      Long assigneeId,
                                                      Long reporterId,
                                                      Pageable pageable) {

        User currentUser = authorizationService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("Full authentication required"));

        Specification<Incident> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Role-based resource visibility scoping
            if (authorizationService.isAdmin()) {
                // Admins see all incidents
            } else if (authorizationService.hasRole(RoleConstants.MANAGER)) {
                // Managers see incidents for their team, or reported by them, or unassigned if no team
                if (currentUser.getTeam() != null) {
                    predicates.add(cb.or(
                            cb.equal(root.get("team").get("id"), currentUser.getTeam().getId()),
                            cb.equal(root.get("reporter").get("id"), currentUser.getId()),
                            cb.isNull(root.get("team"))
                    ));
                }
            } else if (authorizationService.hasRole(RoleConstants.ENGINEER)) {
                // Engineers see assigned incidents, reported incidents, or team queue
                if (currentUser.getTeam() != null) {
                    predicates.add(cb.or(
                            cb.equal(root.get("assignee").get("id"), currentUser.getId()),
                            cb.equal(root.get("reporter").get("id"), currentUser.getId()),
                            cb.equal(root.get("team").get("id"), currentUser.getTeam().getId())
                    ));
                } else {
                    predicates.add(cb.or(
                            cb.equal(root.get("assignee").get("id"), currentUser.getId()),
                            cb.equal(root.get("reporter").get("id"), currentUser.getId())
                    ));
                }
            } else {
                // Standard employees see ONLY their own reported incidents
                predicates.add(cb.equal(root.get("reporter").get("id"), currentUser.getId()));
            }

            // 2. Query parameter filters
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (assigneeId != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), assigneeId));
            }
            if (reporterId != null) {
                predicates.add(cb.equal(root.get("reporter").get("id"), reporterId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Incident> page = incidentRepository.findAll(spec, pageable);
        return PageResponse.of(page, IncidentResponse::fromEntity);
    }

    /**
     * Updates editable details (title, description, category, priority, severity) of an existing incident.
     */
    @Transactional
    public IncidentResponse updateIncident(Long id, IncidentUpdateRequest request, String currentUserEmail) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));

        if (!authorizationService.canUpdateIncident(incident)) {
            throw new AccessDeniedException("Access denied: insufficient permissions to update incident ID: " + id);
        }

        User actor = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserEmail));

        StringBuilder changes = new StringBuilder();

        if (request.getTitle() != null && !request.getTitle().isBlank() && !request.getTitle().equals(incident.getTitle())) {
            changes.append(String.format("title changed from '%s' to '%s'; ", incident.getTitle(), request.getTitle().trim()));
            incident.setTitle(request.getTitle().trim());
        }

        if (request.getDescription() != null && !request.getDescription().isBlank() && !request.getDescription().equals(incident.getDescription())) {
            changes.append("description updated; ");
            incident.setDescription(request.getDescription().trim());
        }

        if (request.getCategoryId() != null && (incident.getCategory() == null || !request.getCategoryId().equals(incident.getCategory().getId()))) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
            String oldCat = incident.getCategory() != null ? incident.getCategory().getName() : "None";
            changes.append(String.format("category changed from '%s' to '%s'; ", oldCat, category.getName()));
            incident.setCategory(category);
        }

        if (request.getPriority() != null && request.getPriority() != incident.getPriority()) {
            changes.append(String.format("priority changed from '%s' to '%s'; ", incident.getPriority(), request.getPriority()));
            incident.setPriority(request.getPriority());
        }

        if (request.getSeverity() != null && request.getSeverity() != incident.getSeverity()) {
            changes.append(String.format("severity changed from '%s' to '%s'; ", incident.getSeverity(), request.getSeverity()));
            incident.setSeverity(request.getSeverity());
        }

        Incident saved = incidentRepository.save(incident);

        if (!changes.isEmpty()) {
            incidentHistoryService.recordHistory(saved, actor, "UPDATED", "details", null, changes.toString());
        }

        return IncidentResponse.fromEntity(saved);
    }

    /**
     * Assigns an incident to an active engineer. Only MANAGER and ADMIN roles are permitted.
     */
    @Transactional
    public IncidentResponse assignIncident(Long id, IncidentAssignRequest request, String currentUserEmail) {
        if (!authorizationService.hasAnyRole(RoleConstants.MANAGER, RoleConstants.ADMIN)) {
            throw new AccessDeniedException("Access denied: only MANAGER or ADMIN roles are authorized to assign incidents");
        }

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));

        User engineer = userRepository.findById(request.getEngineerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getEngineerId()));

        if (Boolean.FALSE.equals(engineer.getIsActive())) {
            throw new IllegalArgumentException("Cannot assign incident to an inactive engineer account");
        }

        if (!RoleConstants.ENGINEER.equalsIgnoreCase(engineer.getRole().getName())) {
            throw new IllegalArgumentException("User ID " + engineer.getId() + " does not have the ENGINEER role");
        }

        User actor = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserEmail));

        String oldAssigneeName = incident.getAssignee() != null
                ? incident.getAssignee().getFirstName() + " " + incident.getAssignee().getLastName()
                : "Unassigned";
        String newAssigneeName = engineer.getFirstName() + " " + engineer.getLastName();

        incident.setAssignee(engineer);
        if (engineer.getTeam() != null && incident.getTeam() == null) {
            incident.setTeam(engineer.getTeam());
        }

        // Direct assignment from NEW or TRIAGED advances status to ASSIGNED
        if (incident.getStatus() == IncidentStatus.NEW || incident.getStatus() == IncidentStatus.TRIAGED) {
            incident.setStatus(IncidentStatus.ASSIGNED);
            incidentHistoryService.recordHistory(incident, actor, "STATUS_CHANGE", "status",
                    IncidentStatus.NEW.name(), IncidentStatus.ASSIGNED.name());
        }

        Incident saved = incidentRepository.save(incident);

        slaService.recordResponse(saved, Instant.now());

        String historyNote = request.getAssignmentReason() != null && !request.getAssignmentReason().isBlank()
                ? newAssigneeName + " (Reason: " + request.getAssignmentReason().trim() + ")"
                : newAssigneeName;

        incidentHistoryService.recordHistory(saved, actor, "ASSIGNED", "assignee", oldAssigneeName, historyNote);

        notificationService.createNotification(
                engineer,
                NotificationType.INCIDENT_ASSIGNED,
                "Incident Assigned: " + saved.getIncidentNumber(),
                "You have been assigned to incident '" + saved.getTitle() + "' with priority " + saved.getPriority() + ".",
                saved.getId()
        );

        log.info("Incident ID {} assigned to engineer {} by {}", id, engineer.getEmail(), actor.getEmail());
        SlaRecord slaRecord = slaService.findSlaRecordByIncidentId(saved.getId()).orElse(null);
        return IncidentResponse.fromEntity(saved, slaRecord);
    }

    /**
     * Transitions an incident to a new lifecycle state with invariant checking and history recording.
     */
    @Transactional
    public IncidentResponse updateStatus(Long id, IncidentStatusUpdateRequest request, String currentUserEmail) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));

        validateStatusTransition(incident.getStatus(), request.getStatus());

        if (!authorizationService.canChangeStatus(incident, request.getStatus())) {
            throw new AccessDeniedException("Access denied: not authorized to transition incident to " + request.getStatus());
        }

        User actor = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserEmail));

        IncidentStatus oldStatus = incident.getStatus();
        incident.setStatus(request.getStatus());

        if (request.getStatus() == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(Instant.now());
        } else if (request.getStatus() == IncidentStatus.CLOSED) {
            incident.setClosedAt(Instant.now());
        } else if (request.getStatus() == IncidentStatus.REOPENED) {
            incident.setResolvedAt(null);
        }

        Incident saved = incidentRepository.save(incident);

        if (request.getStatus() == IncidentStatus.RESOLVED) {
            slaService.recordResolution(saved, saved.getResolvedAt());
        } else if (request.getStatus() == IncidentStatus.REOPENED) {
            slaService.recordReopen(saved);
        } else if (request.getStatus() == IncidentStatus.TRIAGED ||
                   request.getStatus() == IncidentStatus.ASSIGNED ||
                   request.getStatus() == IncidentStatus.IN_PROGRESS) {
            slaService.recordResponse(saved, Instant.now());
        }

        incidentHistoryService.recordHistory(saved, actor, "STATUS_CHANGE", "status", oldStatus.name(), request.getStatus().name());

        sendStatusChangeNotifications(saved, oldStatus, request.getStatus(), actor);

        if (request.getComment() != null && !request.getComment().isBlank()) {
            incidentCommentService.addComment(id,
                    IncidentCommentCreateRequest.builder().commentText(request.getComment().trim()).isInternal(false).build(),
                    currentUserEmail);
        }

        log.info("Incident ID {} status transitioned from {} to {} by {}", id, oldStatus, request.getStatus(), actor.getEmail());
        SlaRecord slaRecord = slaService.findSlaRecordByIncidentId(saved.getId()).orElse(null);
        return IncidentResponse.fromEntity(saved, slaRecord);
    }

    private void sendStatusChangeNotifications(Incident incident, IncidentStatus oldStatus, IncidentStatus newStatus, User actor) {
        User reporter = incident.getReporter();
        User assignee = incident.getAssignee();
        Team team = incident.getTeam();

        if (newStatus == IncidentStatus.RESOLVED) {
            String title = "Incident Resolved: " + incident.getIncidentNumber();
            String msg = "Incident '" + incident.getTitle() + "' has been resolved.";
            if (reporter != null && !reporter.getId().equals(actor.getId())) {
                notificationService.createNotification(reporter, NotificationType.INCIDENT_RESOLVED, title, msg, incident.getId());
            }
            if (assignee != null && !assignee.getId().equals(actor.getId())) {
                notificationService.createNotification(assignee, NotificationType.INCIDENT_RESOLVED, title, msg, incident.getId());
            }
        } else if (newStatus == IncidentStatus.REOPENED) {
            String title = "Incident Reopened: " + incident.getIncidentNumber();
            String msg = "Incident '" + incident.getTitle() + "' has been reopened.";
            if (assignee != null && !assignee.getId().equals(actor.getId())) {
                notificationService.createNotification(assignee, NotificationType.INCIDENT_REOPENED, title, msg, incident.getId());
            } else if (team != null && team.getLeadUser() != null && !team.getLeadUser().getId().equals(actor.getId())) {
                notificationService.createNotification(team.getLeadUser(), NotificationType.INCIDENT_REOPENED, title, msg, incident.getId());
            }
            if (reporter != null && !reporter.getId().equals(actor.getId())) {
                notificationService.createNotification(reporter, NotificationType.INCIDENT_REOPENED, title, msg, incident.getId());
            }
        } else if (newStatus == IncidentStatus.ESCALATED) {
            String title = "Incident Escalated: " + incident.getIncidentNumber();
            String msg = "Incident '" + incident.getTitle() + "' has been escalated.";
            if (assignee != null && !assignee.getId().equals(actor.getId())) {
                notificationService.createNotification(assignee, NotificationType.INCIDENT_ESCALATED, title, msg, incident.getId());
            }
            if (reporter != null && !reporter.getId().equals(actor.getId())) {
                notificationService.createNotification(reporter, NotificationType.INCIDENT_ESCALATED, title, msg, incident.getId());
            }
            if (team != null && team.getLeadUser() != null && !team.getLeadUser().getId().equals(actor.getId())
                    && (assignee == null || !assignee.getId().equals(team.getLeadUser().getId()))) {
                notificationService.createNotification(team.getLeadUser(), NotificationType.INCIDENT_ESCALATED, title, msg, incident.getId());
            }
        } else {
            String title = "Incident Status Updated: " + incident.getIncidentNumber();
            String msg = "Incident '" + incident.getTitle() + "' status changed from " + oldStatus + " to " + newStatus + ".";
            if (reporter != null && !reporter.getId().equals(actor.getId())) {
                notificationService.createNotification(reporter, NotificationType.INCIDENT_STATUS_CHANGED, title, msg, incident.getId());
            }
            if (assignee != null && !assignee.getId().equals(actor.getId())) {
                notificationService.createNotification(assignee, NotificationType.INCIDENT_STATUS_CHANGED, title, msg, incident.getId());
            }
        }
    }

    /**
     * Validates whether a requested status transition adheres to the incident lifecycle state machine.
     */
    public void validateStatusTransition(IncidentStatus currentStatus, IncidentStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            throw new IllegalArgumentException("Incident status values cannot be null");
        }
        if (currentStatus == targetStatus) {
            throw new InvalidIncidentStatusTransitionException(
                    String.format("Incident is already in status '%s'. Transition to the same status is not permitted.", currentStatus));
        }
        Set<IncidentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new InvalidIncidentStatusTransitionException(currentStatus, targetStatus);
        }
    }

    private String generateUniqueIncidentNumber() {
        String datePrefix = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMATTER);
        for (int attempt = 0; attempt < 10; attempt++) {
            int randomSeq = RANDOM.nextInt(10000);
            String incidentNumber = String.format("INC-%s-%04d", datePrefix, randomSeq);
            if (!incidentRepository.existsByIncidentNumber(incidentNumber)) {
                return incidentNumber;
            }
        }
        return "INC-" + datePrefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
