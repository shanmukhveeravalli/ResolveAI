package com.resolveai.incident.exception;

import com.resolveai.incident.entity.IncidentStatus;
import lombok.Getter;

/**
 * Thrown when an illegal lifecycle transition is requested on an Incident.
 */
@Getter
public class InvalidIncidentStatusTransitionException extends RuntimeException {

    private final IncidentStatus fromStatus;
    private final IncidentStatus toStatus;

    public InvalidIncidentStatusTransitionException(IncidentStatus fromStatus, IncidentStatus toStatus) {
        super(String.format("Invalid incident status transition from '%s' to '%s'. This transition is not permitted by lifecycle rules.",
                fromStatus, toStatus));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public InvalidIncidentStatusTransitionException(String message) {
        super(message);
        this.fromStatus = null;
        this.toStatus = null;
    }
}
