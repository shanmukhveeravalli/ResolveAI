package com.resolveai.analytics.dto;

import com.resolveai.incident.entity.Severity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeverityCountResponse {
    private Severity severity;
    private long count;
}
