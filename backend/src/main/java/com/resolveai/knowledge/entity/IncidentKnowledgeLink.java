package com.resolveai.knowledge.entity;

import com.resolveai.incident.entity.Incident;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Link Entity between Incidents and Knowledge Base Articles.
 */
@Entity
@Table(
    name = "incident_knowledge_links",
    uniqueConstraints = @UniqueConstraint(name = "uk_incident_article", columnNames = {"incident_id", "article_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentKnowledgeLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private KnowledgeArticle article;

    @Builder.Default
    @Column(name = "relevance_score", precision = 5, scale = 4)
    private BigDecimal relevanceScore = BigDecimal.valueOf(1.0000);

    @Builder.Default
    @Column(name = "linked_by_ai", nullable = false)
    private Boolean linkedByAi = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
