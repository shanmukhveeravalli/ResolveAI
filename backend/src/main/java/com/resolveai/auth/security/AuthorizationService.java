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
        User currentUser = getCurrentUser().orElse(null);
        if (hasRole(RoleConstants.MANAGER) && currentUser != null && currentUser.getTeam() != null) {
            return currentUser.getTeam().getId().equals(teamId);
        }
        return false;
    }
}
