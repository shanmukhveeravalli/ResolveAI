package com.resolveai.analytics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnalyticsOverviewResponse {
    private long totalIncidents;
    private long openIncidents;
    private long resolvedIncidents;
    private long closedIncidents;
    private long escalatedIncidents;
    private long reopenedIncidents;
}
