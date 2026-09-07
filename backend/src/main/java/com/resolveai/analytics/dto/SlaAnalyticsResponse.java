package com.resolveai.analytics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SlaAnalyticsResponse {
    private long totalSlaRecords;
    private long responseBreaches;
    private long resolutionBreaches;
    private long totalBreachedRecords;
}
