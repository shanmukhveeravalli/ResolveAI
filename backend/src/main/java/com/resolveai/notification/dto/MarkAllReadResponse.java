package com.resolveai.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned when bulk marking notifications as read.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkAllReadResponse {

    private int updatedCount;
    private String message;
}
