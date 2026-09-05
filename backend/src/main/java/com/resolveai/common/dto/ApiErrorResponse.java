package com.resolveai.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard API error response format.
 * 
 * Conforms to ResolveAI error specification:
 * {
 *   "timestamp": "...",
 *   "status": 400,
 *   "error": "VALIDATION_ERROR",
 *   "message": "Invalid request",
 *   "path": "/api/incidents",
 *   "details": []
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    @Builder.Default
    private Instant timestamp = Instant.now();

    private int status;

    private String error;

    private String message;

    private String path;

    @Builder.Default
    private List<ValidationErrorDetail> details = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationErrorDetail {
        private String field;
        private String issue;
    }
}
