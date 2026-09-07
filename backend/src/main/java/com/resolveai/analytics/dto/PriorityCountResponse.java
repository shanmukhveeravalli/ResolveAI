package com.resolveai.analytics.dto;

import com.resolveai.incident.entity.Priority;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PriorityCountResponse {
    private Priority priority;
    private long count;
}
