package com.resolveai.analytics.repository;

import com.resolveai.analytics.dto.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class AnalyticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public AnalyticsOverviewResponse getIncidentOverview(Instant from, Instant to) {
        String jpql = "SELECT " +
                "COUNT(i), " +
                "SUM(CASE WHEN i.status IN ('NEW', 'TRIAGED', 'ASSIGNED', 'IN_PROGRESS') THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN i.status = 'RESOLVED' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN i.status = 'CLOSED' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN i.status = 'ESCALATED' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN i.status = 'REOPENED' THEN 1 ELSE 0 END) " +
                "FROM Incident i WHERE (:fromDate IS NULL OR i.createdAt >= :fromDate) AND (:toDate IS NULL OR i.createdAt <= :toDate)";
        
        Object[] result = (Object[]) entityManager.createQuery(jpql)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
                .getSingleResult();

        return AnalyticsOverviewResponse.builder()
                .totalIncidents(result[0] != null ? ((Number) result[0]).longValue() : 0)
                .openIncidents(result[1] != null ? ((Number) result[1]).longValue() : 0)
                .resolvedIncidents(result[2] != null ? ((Number) result[2]).longValue() : 0)
                .closedIncidents(result[3] != null ? ((Number) result[3]).longValue() : 0)
                .escalatedIncidents(result[4] != null ? ((Number) result[4]).longValue() : 0)
                .reopenedIncidents(result[5] != null ? ((Number) result[5]).longValue() : 0)
                .build();
    }

    public List<StatusCountResponse> getStatusBreakdown(Instant from, Instant to) {
        String jpql = "SELECT new com.resolveai.analytics.dto.StatusCountResponse(i.status, COUNT(i)) " +
                "FROM Incident i WHERE (:fromDate IS NULL OR i.createdAt >= :fromDate) AND (:toDate IS NULL OR i.createdAt <= :toDate) " +
                "GROUP BY i.status";
        return entityManager.createQuery(jpql, StatusCountResponse.class)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
                .getResultList();
    }

    public List<PriorityCountResponse> getPriorityBreakdown(Instant from, Instant to) {
        String jpql = "SELECT new com.resolveai.analytics.dto.PriorityCountResponse(i.priority, COUNT(i)) " +
                "FROM Incident i WHERE (:fromDate IS NULL OR i.createdAt >= :fromDate) AND (:toDate IS NULL OR i.createdAt <= :toDate) " +
                "GROUP BY i.priority";
        return entityManager.createQuery(jpql, PriorityCountResponse.class)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
                .getResultList();
    }

    public List<SeverityCountResponse> getSeverityBreakdown(Instant from, Instant to) {
        String jpql = "SELECT new com.resolveai.analytics.dto.SeverityCountResponse(i.severity, COUNT(i)) " +
                "FROM Incident i WHERE (:fromDate IS NULL OR i.createdAt >= :fromDate) AND (:toDate IS NULL OR i.createdAt <= :toDate) " +
                "GROUP BY i.severity";
        return entityManager.createQuery(jpql, SeverityCountResponse.class)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
                .getResultList();
    }

    public List<TeamWorkloadResponse> getTeamWorkload() {
        String jpql = "SELECT new com.resolveai.analytics.dto.TeamWorkloadResponse(t.id, t.name, COUNT(i)) " +
                "FROM Team t LEFT JOIN Incident i ON i.team = t AND i.status IN ('NEW', 'TRIAGED', 'ASSIGNED', 'IN_PROGRESS', 'ESCALATED', 'REOPENED') " +
                "GROUP BY t.id, t.name";
        return entityManager.createQuery(jpql, TeamWorkloadResponse.class).getResultList();
    }

    public SlaAnalyticsResponse getSlaMetrics() {
        String jpql = "SELECT " +
                "COUNT(s), " +
                "SUM(CASE WHEN s.isResponseBreached = true THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN s.isResolutionBreached = true THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN (s.isResponseBreached = true OR s.isResolutionBreached = true) THEN 1 ELSE 0 END) " +
                "FROM SlaRecord s";
        
        Object[] result = (Object[]) entityManager.createQuery(jpql).getSingleResult();

        return SlaAnalyticsResponse.builder()
                .totalSlaRecords(result[0] != null ? ((Number) result[0]).longValue() : 0)
                .responseBreaches(result[1] != null ? ((Number) result[1]).longValue() : 0)
                .resolutionBreaches(result[2] != null ? ((Number) result[2]).longValue() : 0)
                .totalBreachedRecords(result[3] != null ? ((Number) result[3]).longValue() : 0)
                .build();
    }

    public KnowledgeAnalyticsResponse getKnowledgeMetrics() {
        String jpql = "SELECT " +
                "COUNT(k), " +
                "SUM(CASE WHEN k.status = 'PUBLISHED' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN k.status = 'DRAFT' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN k.status = 'ARCHIVED' THEN 1 ELSE 0 END) " +
                "FROM KnowledgeArticle k";
        
        Object[] result = (Object[]) entityManager.createQuery(jpql).getSingleResult();

        return KnowledgeAnalyticsResponse.builder()
                .totalArticles(result[0] != null ? ((Number) result[0]).longValue() : 0)
                .publishedArticles(result[1] != null ? ((Number) result[1]).longValue() : 0)
                .draftArticles(result[2] != null ? ((Number) result[2]).longValue() : 0)
                .archivedArticles(result[3] != null ? ((Number) result[3]).longValue() : 0)
                .build();
    }
}
