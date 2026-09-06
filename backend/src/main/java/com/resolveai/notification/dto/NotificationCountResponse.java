package com.resolveai.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returning the unread notification count.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCountResponse {

    private long count;
}
