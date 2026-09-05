package com.resolveai.incident.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resolveai.incident.entity.IncidentHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for incident audit history records.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncidentHistoryResponse {

    private Long id;
    private Long incidentId;
    private Long actorId;
    private String actorName;
    private String actorEmail;
    private String actionType;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private Instant createdAt;

    public static IncidentHistoryResponse fromEntity(IncidentHistory history) {
        if (history == null) {
            return null;
        }

        IncidentHistoryResponseBuilder builder = IncidentHistoryResponse.builder()
                .id(history.getId())
                .actionType(history.getActionType())
                .fieldName(history.getFieldName())
                .oldValue(history.getOldValue())
                .newValue(history.getNewValue())
                .createdAt(history.getCreatedAt());

        if (history.getIncident() != null) {
            builder.incidentId(history.getIncident().getId());
        }

        if (history.getActor() != null) {
            builder.actorId(history.getActor().getId())
                    .actorName(history.getActor().getFirstName() + " " + history.getActor().getLastName())
                    .actorEmail(history.getActor().getEmail());
        }

        return builder.build();
    }
}
