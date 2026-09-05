package com.resolveai.incident.entity;

/**
 * Formal Incident Lifecycle States.
 */
public enum IncidentStatus {
    NEW,
    TRIAGED,
    ASSIGNED,
    IN_PROGRESS,
    ESCALATED,
    RESOLVED,
    CLOSED,
    REOPENED
}
