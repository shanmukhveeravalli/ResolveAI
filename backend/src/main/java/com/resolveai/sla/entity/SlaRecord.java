package com.resolveai.sla.entity;

import com.resolveai.common.entity.BaseAuditEntity;
import com.resolveai.incident.entity.Incident;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * SLA Record Tracking Entity (1:1 with Incident).
 */
@Entity
@Table(name = "sla_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaRecord extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false, unique = true)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_policy_id", nullable = false)
    private SlaPolicy slaPolicy;

    @Column(name = "response_due_at", nullable = false)
    private Instant responseDueAt;

    @Column(name = "resolution_due_at", nullable = false)
    private Instant resolutionDueAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Builder.Default
    @Column(name = "is_response_breached", nullable = false)
    private Boolean isResponseBreached = false;

    @Builder.Default
    @Column(name = "is_resolution_breached", nullable = false)
    private Boolean isResolutionBreached = false;
}
