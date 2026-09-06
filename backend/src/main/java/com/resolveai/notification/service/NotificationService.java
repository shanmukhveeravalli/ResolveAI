package com.resolveai.notification.service;

import com.resolveai.common.dto.PageResponse;
import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.notification.dto.MarkAllReadResponse;
import com.resolveai.notification.dto.NotificationResponse;
import com.resolveai.notification.entity.Notification;
import com.resolveai.notification.entity.NotificationType;
import com.resolveai.notification.repository.NotificationRepository;
import com.resolveai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Centralized service managing in-app notifications, user scoped retrieval,
 * and read-state transitions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Creates and persists an in-app notification with enum type.
     * Wrapped with safe error handling so notification issues do not disrupt caller transactions.
     */
    @Transactional
    public Notification createNotification(User recipient, NotificationType type, String title, String message, Long referenceId) {
        String typeName = type != null ? type.name() : "GENERAL";
        return createNotification(recipient, typeName, title, message, referenceId);
    }

    /**
     * Creates and persists an in-app notification with string type.
     */
    @Transactional
    public Notification createNotification(User recipient, String type, String title, String message, Long referenceId) {
        if (recipient == null) {
            log.warn("Cannot create notification: recipient user is null");
            return null;
        }

        try {
            Notification notification = Notification.builder()
                    .user(recipient)
                    .title(title != null ? title.trim() : "Notification")
                    .message(message != null ? message.trim() : "")
                    .type(type != null ? type.trim() : "GENERAL")
                    .referenceId(referenceId)
                    .isRead(false)
                    .build();

            Notification saved = notificationRepository.save(notification);
            log.info("Notification ID {} created for user ID {} with type {}", saved.getId(), recipient.getId(), type);
            return saved;
        } catch (Exception ex) {
            log.error("Failed to create notification for user ID {}: {}", recipient.getId(), ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Retrieves paginated notifications for the authenticated user, ordered by creation time descending.
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getUserNotifications(Long userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(page, NotificationResponse::fromEntity);
    }

    /**
     * Counts unread notifications for the specified user.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * Marks a specific notification as read.
     * Enforces that the notification belongs to the authenticated user.
     *
     * @param notificationId ID of the notification
     * @param currentUserId ID of the current authenticated user
     * @return Updated NotificationResponse
     * @throws ResourceNotFoundException if notification doesn't exist
     * @throws AccessDeniedException if notification belongs to another user
     */
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long currentUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        if (!notification.getUser().getId().equals(currentUserId)) {
            log.warn("Security violation: user ID {} attempted to mark notification ID {} belonging to user ID {}",
                    currentUserId, notificationId, notification.getUser().getId());
            throw new AccessDeniedException("Access denied: notification does not belong to the current user");
        }

        if (Boolean.FALSE.equals(notification.getIsRead()) || notification.getIsRead() == null) {
            notification.setIsRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
            log.info("Notification ID {} marked as read by user ID {}", notificationId, currentUserId);
        }

        return NotificationResponse.fromEntity(notification);
    }

    /**
     * Marks all unread notifications belonging to the current user as read.
     *
     * @param currentUserId ID of the current authenticated user
     * @return MarkAllReadResponse containing count of updated notifications
     */
    @Transactional
    public MarkAllReadResponse markAllAsRead(Long currentUserId) {
        int updatedCount = notificationRepository.markAllAsReadForUser(currentUserId, Instant.now());
        log.info("Marked {} unread notifications as read for user ID {}", updatedCount, currentUserId);
        return MarkAllReadResponse.builder()
                .updatedCount(updatedCount)
                .message("All unread notifications marked as read")
                .build();
    }
}
