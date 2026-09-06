package com.resolveai.notification.entity;

/**
 * Supported enterprise notification types for business events.
 */
public enum NotificationType {
    INCIDENT_CREATED,
    INCIDENT_ASSIGNED,
    INCIDENT_STATUS_CHANGED,
    INCIDENT_COMMENT_ADDED,
    INCIDENT_ESCALATED,
    INCIDENT_RESOLVED,
    INCIDENT_REOPENED,
    SLA_BREACHED,
    TEAM_ASSIGNMENT
}
