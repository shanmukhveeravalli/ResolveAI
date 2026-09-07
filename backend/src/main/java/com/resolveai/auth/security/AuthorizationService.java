package com.resolveai.auth.security;

import com.resolveai.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Reusable authorization service providing programmatically accessible and SpEL-compatible
 * security utilities. Serves as the foundation for resource-level authorization in future phases.
 */
@Service("authorizationService")
public class AuthorizationService {

    /**
     * Retrieves the currently authenticated User entity from the SecurityContext, if available.
     */
    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser securityUser) {
            return Optional.of(securityUser.getUser());
        }
        return Optional.empty();
    }

    /**
     * Retrieves the user ID of the currently authenticated user.
     */
    public Optional<Long> getCurrentUserId() {
        return getCurrentUser().map(User::getId);
    }

    /**
     * Retrieves the email address of the currently authenticated user.
     */
    public Optional<String> getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return Optional.ofNullable(authentication.getName());
        }
        return Optional.empty();
    }

    /**
     * Checks if the currently authenticated user possesses the specified role.
     * Accepts either raw role names (e.g. "ADMIN") or prefixed authorities (e.g. "ROLE_ADMIN").
     */
    public boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String targetAuthority = role.startsWith(RoleConstants.ROLE_PREFIX)
                ? role
                : RoleConstants.ROLE_PREFIX + role;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals(targetAuthority));
    }

    /**
     * Checks if the currently authenticated user possesses any of the specified roles.
     */
    public boolean hasAnyRole(String... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }
        return Arrays.stream(roles).anyMatch(this::hasRole);
    }

    /**
     * Convenience method to check if the current user is an administrator.
     */
    public boolean isAdmin() {
        return hasRole(RoleConstants.ADMIN);
    }

    /**
     * Validates whether the given user ID corresponds to the currently authenticated user.
     */
    public boolean isCurrentUser(Long userId) {
        if (userId == null) {
            return false;
        }
        return getCurrentUserId().map(userId::equals).orElse(false);
    }

    /**
     * Extensible hook for Phase 5 resource-level incident authorization:
     * - EMPLOYEE: access own incidents (as reporter)
     * - ENGINEER: access assigned or team incidents
     * - MANAGER: access team incidents
     * - ADMIN: access all incidents
     */
    public boolean canAccessIncident(Long reporterId, Long assigneeId, Long teamId) {
        if (isAdmin()) {
            return true;
        }
        Long currentUserId = getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            return false;
        }
        if (hasRole(RoleConstants.EMPLOYEE) && currentUserId.equals(reporterId)) {
            return true;
        }
        if (hasRole(RoleConstants.ENGINEER) && (currentUserId.equals(assigneeId) || currentUserId.equals(reporterId))) {
            return true;
        }
        if (hasRole(RoleConstants.MANAGER)) {
            if (currentUserId.equals(reporterId)) {
                return true;
            }
            User currentUser = getCurrentUser().orElse(null);
            if (currentUser != null && currentUser.getTeam() != null) {
                return currentUser.getTeam().getId().equals(teamId);
            }
        }
        return false;
    }

    /**
     * Checks if the currently authenticated user can view the given incident entity.
     */
    public boolean canAccessIncident(com.resolveai.incident.entity.Incident incident) {
        if (incident == null) {
            return false;
        }
        Long reporterId = incident.getReporter() != null ? incident.getReporter().getId() : null;
        Long assigneeId = incident.getAssignee() != null ? incident.getAssignee().getId() : null;
        Long teamId = incident.getTeam() != null ? incident.getTeam().getId() : null;
        return canAccessIncident(reporterId, assigneeId, teamId);
    }

    /**
     * Checks if the currently authenticated user can edit incident details (title, description, etc.).
     * - ADMIN, MANAGER: can update any accessible incident
     * - ENGINEER: can update if assigned or reporter
     * - EMPLOYEE: can update only if reporter and status is still NEW
     */
    public boolean canUpdateIncident(com.resolveai.incident.entity.Incident incident) {
        if (!canAccessIncident(incident)) {
            return false;
        }
        if (isAdmin() || hasRole(RoleConstants.MANAGER) || hasRole(RoleConstants.ENGINEER)) {
            return true;
        }
        if (hasRole(RoleConstants.EMPLOYEE)) {
            return incident.getStatus() == com.resolveai.incident.entity.IncidentStatus.NEW;
        }
        return false;
    }

    /**
     * Checks if the currently authenticated user is authorized to perform the requested status transition.
     */
    public boolean canChangeStatus(com.resolveai.incident.entity.Incident incident, com.resolveai.incident.entity.IncidentStatus targetStatus) {
        if (!canAccessIncident(incident)) {
            return false;
        }
        if (isAdmin() || hasRole(RoleConstants.MANAGER)) {
            return true;
        }
        if (hasRole(RoleConstants.ENGINEER)) {
            return true;
        }
        if (hasRole(RoleConstants.EMPLOYEE)) {
            // Employees can only close or reopen a resolved incident
            return (targetStatus == com.resolveai.incident.entity.IncidentStatus.CLOSED ||
                    targetStatus == com.resolveai.incident.entity.IncidentStatus.REOPENED)
                    && incident.getStatus() == com.resolveai.incident.entity.IncidentStatus.RESOLVED;
        }
        return false;
    }

    /**
     * Checks if the currently authenticated user can create knowledge articles.
     */
    public boolean canCreateKnowledgeArticle() {
        return hasAnyRole(RoleConstants.ENGINEER, RoleConstants.MANAGER, RoleConstants.ADMIN);
    }

    /**
     * Checks if the currently authenticated user can view the given knowledge article:
     * - PUBLISHED: all authenticated users can view
     * - DRAFT / ARCHIVED: only ADMIN, MANAGER, or author ENGINEER
     */
    public boolean canViewKnowledgeArticle(com.resolveai.knowledge.entity.KnowledgeArticle article) {
        if (article == null) {
            return false;
        }
        if (article.getStatus() == com.resolveai.knowledge.entity.KnowledgeArticleStatus.PUBLISHED) {
            return true;
        }
        if (isAdmin() || hasRole(RoleConstants.MANAGER)) {
            return true;
        }
        if (hasRole(RoleConstants.ENGINEER)) {
            Long authorId = article.getAuthor() != null ? article.getAuthor().getId() : null;
            return isCurrentUser(authorId);
        }
        return false;
    }

    /**
     * Checks if the currently authenticated user can update, publish, or archive a knowledge article:
     * - ADMIN, MANAGER: can manage any article
     * - ENGINEER: can manage only their own articles
     * - EMPLOYEE: forbidden
     */
    public boolean canManageKnowledgeArticle(com.resolveai.knowledge.entity.KnowledgeArticle article) {
        if (article == null) {
            return false;
        }
        if (isAdmin() || hasRole(RoleConstants.MANAGER)) {
            return true;
        }
        if (hasRole(RoleConstants.ENGINEER)) {
            Long authorId = article.getAuthor() != null ? article.getAuthor().getId() : null;
            return isCurrentUser(authorId);
        }
        return false;
    }
}
