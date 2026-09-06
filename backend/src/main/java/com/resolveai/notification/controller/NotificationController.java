package com.resolveai.notification.controller;

import com.resolveai.auth.security.AuthorizationService;
import com.resolveai.common.dto.PageResponse;
import com.resolveai.notification.dto.MarkAllReadResponse;
import com.resolveai.notification.dto.NotificationCountResponse;
import com.resolveai.notification.dto.NotificationResponse;
import com.resolveai.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller exposing notification management endpoints.
 * All operations are strictly scoped to the authenticated user.
 */
@Tag(name = "Notifications", description = "In-App Notification Management Endpoints")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthorizationService authorizationService;

    /**
     * Retrieves paginated notifications for the authenticated user.
     */
    @Operation(summary = "Get user notifications", description = "Retrieves paginated notifications for the current authenticated user.")
    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> getNotifications(
            @PageableDefault(size = 20) Pageable pageable) {

        Long currentUserId = authorizationService.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Full authentication required"));

        PageResponse<NotificationResponse> response = notificationService.getUserNotifications(currentUserId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the count of unread notifications for the authenticated user.
     */
    @Operation(summary = "Get unread count", description = "Returns the number of unread notifications for the current authenticated user.")
    @GetMapping("/unread-count")
    public ResponseEntity<NotificationCountResponse> getUnreadCount() {
        Long currentUserId = authorizationService.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Full authentication required"));

        long count = notificationService.getUnreadCount(currentUserId);
        return ResponseEntity.ok(NotificationCountResponse.builder().count(count).build());
    }

    /**
     * Marks a specific notification as read. Ownership is validated.
     */
    @Operation(summary = "Mark notification as read", description = "Marks a single notification as read if owned by the current user.")
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        Long currentUserId = authorizationService.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Full authentication required"));

        NotificationResponse response = notificationService.markAsRead(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Marks all unread notifications belonging to the authenticated user as read.
     */
    @Operation(summary = "Mark all notifications as read", description = "Marks all unread notifications for the authenticated user as read.")
    @PatchMapping("/read-all")
    public ResponseEntity<MarkAllReadResponse> markAllAsRead() {
        Long currentUserId = authorizationService.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Full authentication required"));

        MarkAllReadResponse response = notificationService.markAllAsRead(currentUserId);
        return ResponseEntity.ok(response);
    }
}
