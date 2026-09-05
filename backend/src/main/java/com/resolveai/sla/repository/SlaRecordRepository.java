package com.resolveai.sla.repository;

import com.resolveai.sla.entity.SlaRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SlaRecordRepository extends JpaRepository<SlaRecord, Long> {
    Optional<SlaRecord> findByIncidentId(Long incidentId);
    List<SlaRecord> findByIsResponseBreachedFalseAndRespondedAtIsNullAndResponseDueAtBefore(Instant time);
    List<SlaRecord> findByIsResolutionBreachedFalseAndResolvedAtIsNullAndResolutionDueAtBefore(Instant time);
}
