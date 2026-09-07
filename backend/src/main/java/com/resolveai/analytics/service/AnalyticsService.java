package com.resolveai.analytics.service;

import com.resolveai.analytics.dto.*;
import com.resolveai.analytics.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getIncidentOverview(Instant from, Instant to) {
        return analyticsRepository.getIncidentOverview(from, to);
    }

    @Transactional(readOnly = true)
    public List<StatusCountResponse> getStatusBreakdown(Instant from, Instant to) {
        return analyticsRepository.getStatusBreakdown(from, to);
    }

    @Transactional(readOnly = true)
    public List<PriorityCountResponse> getPriorityBreakdown(Instant from, Instant to) {
        return analyticsRepository.getPriorityBreakdown(from, to);
    }

    @Transactional(readOnly = true)
    public List<SeverityCountResponse> getSeverityBreakdown(Instant from, Instant to) {
        return analyticsRepository.getSeverityBreakdown(from, to);
    }

    @Transactional(readOnly = true)
    public List<TeamWorkloadResponse> getTeamWorkload() {
        return analyticsRepository.getTeamWorkload();
    }

    @Transactional(readOnly = true)
    public SlaAnalyticsResponse getSlaMetrics() {
        return analyticsRepository.getSlaMetrics();
    }

    @Transactional(readOnly = true)
    public KnowledgeAnalyticsResponse getKnowledgeMetrics() {
        return analyticsRepository.getKnowledgeMetrics();
    }
}
