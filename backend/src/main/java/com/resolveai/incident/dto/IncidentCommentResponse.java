package com.resolveai.incident.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resolveai.incident.entity.IncidentComment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for incident comments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncidentCommentResponse {

    private Long id;
    private Long incidentId;
    private Long authorId;
    private String authorName;
    private String authorEmail;
    private String commentText;
    private Boolean isInternal;
    private Instant createdAt;
    private Instant updatedAt;

    public static IncidentCommentResponse fromEntity(IncidentComment comment) {
        if (comment == null) {
            return null;
        }

        IncidentCommentResponseBuilder builder = IncidentCommentResponse.builder()
                .id(comment.getId())
                .commentText(comment.getCommentText())
                .isInternal(comment.getIsInternal())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt());

        if (comment.getIncident() != null) {
            builder.incidentId(comment.getIncident().getId());
        }

        if (comment.getAuthor() != null) {
            builder.authorId(comment.getAuthor().getId())
                    .authorName(comment.getAuthor().getFirstName() + " " + comment.getAuthor().getLastName())
                    .authorEmail(comment.getAuthor().getEmail());
        }

        return builder.build();
    }
}
