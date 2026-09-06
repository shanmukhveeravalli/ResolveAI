package com.resolveai.incident.service;

import com.resolveai.auth.security.AuthorizationService;
import com.resolveai.auth.security.RoleConstants;
import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.incident.dto.IncidentCommentCreateRequest;
import com.resolveai.incident.dto.IncidentCommentResponse;
import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentComment;
import com.resolveai.incident.repository.IncidentCommentRepository;
import com.resolveai.incident.repository.IncidentRepository;
import com.resolveai.notification.entity.NotificationType;
import com.resolveai.notification.service.NotificationService;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing comments and internal notes on incidents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentCommentService {

    private final IncidentCommentRepository incidentCommentRepository;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;

    /**
     * Adds a comment to an incident.
     */
    @Transactional
    public IncidentCommentResponse addComment(Long incidentId,
                                             IncidentCommentCreateRequest request,
                                             String currentUserEmail) {

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        if (!authorizationService.canAccessIncident(incident)) {
            throw new AccessDeniedException("Access denied: insufficient permissions to comment on incident ID: " + incidentId);
        }

        User author = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserEmail));

        boolean isInternal = Boolean.TRUE.equals(request.getIsInternal());
        // Employees cannot post internal investigation notes
        if (isInternal && authorizationService.hasRole(RoleConstants.EMPLOYEE)
                && !authorizationService.hasAnyRole(RoleConstants.ENGINEER, RoleConstants.MANAGER, RoleConstants.ADMIN)) {
            isInternal = false;
        }

        IncidentComment comment = IncidentComment.builder()
                .incident(incident)
                .author(author)
                .commentText(request.getCommentText().trim())
                .isInternal(isInternal)
                .build();

        IncidentComment saved = incidentCommentRepository.save(comment);
        log.info("Comment ID {} added to incident ID {} by user {}", saved.getId(), incidentId, author.getEmail());

        String notifTitle = "New Comment on Incident: " + incident.getIncidentNumber();
        String notifMsg = String.format("A new comment was added to incident '%s' by %s %s.",
                incident.getTitle(), author.getFirstName(), author.getLastName());

        // Notify reporter if not internal and reporter is not the author
        if (!isInternal && incident.getReporter() != null && !incident.getReporter().getId().equals(author.getId())) {
            notificationService.createNotification(
                    incident.getReporter(),
                    NotificationType.INCIDENT_COMMENT_ADDED,
                    notifTitle,
                    notifMsg,
                    incident.getId()
            );
        }

        // Notify assignee if assignee exists and is not the author
        if (incident.getAssignee() != null && !incident.getAssignee().getId().equals(author.getId())) {
            notificationService.createNotification(
                    incident.getAssignee(),
                    NotificationType.INCIDENT_COMMENT_ADDED,
                    notifTitle,
                    notifMsg,
                    incident.getId()
            );
        }

        return IncidentCommentResponse.fromEntity(saved);
    }

    /**
     * Retrieves all comments visible to the authenticated user for an incident.
     */
    @Transactional(readOnly = true)
    public List<IncidentCommentResponse> getComments(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        if (!authorizationService.canAccessIncident(incident)) {
            throw new AccessDeniedException("Access denied: insufficient permissions to view comments for incident ID: " + incidentId);
        }

        List<IncidentComment> comments;
        if (authorizationService.hasRole(RoleConstants.EMPLOYEE)
                && !authorizationService.hasAnyRole(RoleConstants.ENGINEER, RoleConstants.MANAGER, RoleConstants.ADMIN)) {
            // Employees see only customer-facing comments
            comments = incidentCommentRepository.findByIncidentIdAndIsInternalFalseOrderByCreatedAtAsc(incidentId);
        } else {
            // Technical staff and Admins see all comments including internal notes
            comments = incidentCommentRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
        }

        return comments.stream()
                .map(IncidentCommentResponse::fromEntity)
                .toList();
    }
}
