package com.resolveai.incident.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating an incident comment or investigation note.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentCommentCreateRequest {

    @NotBlank(message = "Comment text is required")
    @Size(min = 1, max = 2000, message = "Comment must be between 1 and 2000 characters")
    @JsonAlias({"comment", "commentText"})
    @JsonProperty("commentText")
    private String commentText;

    @Builder.Default
    private Boolean isInternal = false;

    // Helper to allow getComment() as well
    public String getComment() {
        return commentText;
    }

    public void setComment(String comment) {
        this.commentText = comment;
    }
}
