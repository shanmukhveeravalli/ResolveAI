package com.resolveai.analytics.dto;

import com.resolveai.incident.entity.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatusCountResponse {
    private IncidentStatus status;
    private long count;
}
