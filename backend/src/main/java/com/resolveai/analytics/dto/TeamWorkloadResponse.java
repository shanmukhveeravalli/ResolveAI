package com.resolveai.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamWorkloadResponse {
    private Long teamId;
    private String teamName;
    private long assignedOpenIncidentCount;
}
