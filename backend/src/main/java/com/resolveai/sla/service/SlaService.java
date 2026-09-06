package com.resolveai.sla.service;

import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.incident.entity.Incident;
import com.resolveai.notification.entity.NotificationType;
import com.resolveai.notification.repository.NotificationRepository;
import com.resolveai.notification.service.NotificationService;
import com.resolveai.sla.dto.SlaRecordResponse;
import com.resolveai.sla.entity.SlaPolicy;
import com.resolveai.sla.entity.SlaRecord;
import com.resolveai.sla.repository.SlaPolicyRepository;
import com.resolveai.sla.repository.SlaRecordRepository;
import com.resolveai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service managing SLA records, dynamic deadline calculations, and response/resolution breach evaluation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaService {

    private final SlaRecordRepository slaRecordRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    /**
     * Determines applicable SLA policy for the incident's priority, calculates response/resolution deadlines,
     * and associates an SLA record with the incident.
     */
    @Transactional
    public SlaRecord createSlaRecordForIncident(Incident incident) {
        if (incident == null) {
            return null;
        }

        SlaPolicy policy = slaPolicyRepository.findByPriority(incident.getPriority())
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElse(null);

        if (policy == null) {
            log.warn("No active SLA policy found for priority {}. Skipping SLA record creation for incident ID {}",
                    incident.getPriority(), incident.getId());
            return null;
        }

        Instant baseTime = incident.getCreatedAt() != null ? incident.getCreatedAt() : Instant.now();
        Instant responseDueAt = baseTime.plus(Duration.ofMinutes(policy.getResponseTimeMinutes()));
        Instant resolutionDueAt = baseTime.plus(Duration.ofMinutes(policy.getResolutionTimeMinutes()));

        SlaRecord record = SlaRecord.builder()
                .incident(incident)
                .slaPolicy(policy)
                .responseDueAt(responseDueAt)
                .resolutionDueAt(resolutionDueAt)
                .isResponseBreached(false)
                .isResolutionBreached(false)
                .build();

        SlaRecord saved = slaRecordRepository.save(record);
        log.info("Created SLA record ID {} for incident ID {} (Response due: {}, Resolution due: {})",
                saved.getId(), incident.getId(), responseDueAt, resolutionDueAt);

        return saved;
    }

    /**
     * Records the initial response timestamp and evaluates if response SLA target was met or breached.
     */
    @Transactional
    public Optional<SlaRecord> recordResponse(Incident incident, Instant respondedAt) {
        if (incident == null) {
            return Optional.empty();
        }

        return slaRecordRepository.findByIncidentId(incident.getId()).map(record -> {
            if (record.getRespondedAt() == null) {
                Instant responseTime = respondedAt != null ? respondedAt : Instant.now();
                record.setRespondedAt(responseTime);

                if (responseTime.isAfter(record.getResponseDueAt())) {
                    record.setIsResponseBreached(true);
                    log.warn("SLA Response breached for incident ID {}. Due: {}, Responded: {}",
                            incident.getId(), record.getResponseDueAt(), responseTime);
                    notifySlaBreach(incident, "Response SLA target has been breached");
                } else {
                    log.info("SLA Response met for incident ID {}. Due: {}, Responded: {}",
                            incident.getId(), record.getResponseDueAt(), responseTime);
                }

                return slaRecordRepository.save(record);
            }
            return record;
        });
    }

    /**
     * Records incident resolution timestamp and evaluates if resolution SLA target was met or breached.
     */
    @Transactional
    public Optional<SlaRecord> recordResolution(Incident incident, Instant resolvedAt) {
        if (incident == null) {
            return Optional.empty();
        }

        return slaRecordRepository.findByIncidentId(incident.getId()).map(record -> {
            Instant resolutionTime = resolvedAt != null ? resolvedAt : Instant.now();
            record.setResolvedAt(resolutionTime);

            if (resolutionTime.isAfter(record.getResolutionDueAt())) {
                record.setIsResolutionBreached(true);
                log.warn("SLA Resolution breached for incident ID {}. Due: {}, Resolved: {}",
                        incident.getId(), record.getResolutionDueAt(), resolutionTime);
                notifySlaBreach(incident, "Resolution SLA target has been breached");
            } else {
                log.info("SLA Resolution met for incident ID {}. Due: {}, Resolved: {}",
                        incident.getId(), record.getResolutionDueAt(), resolutionTime);
            }

            return slaRecordRepository.save(record);
        });
    }

    /**
     * Handles ticket reopen event by clearing the resolved timestamp.
     */
    @Transactional
    public Optional<SlaRecord> recordReopen(Incident incident) {
        if (incident == null) {
            return Optional.empty();
        }

        return slaRecordRepository.findByIncidentId(incident.getId()).map(record -> {
            record.setResolvedAt(null);
            // If the deadline hasn't elapsed yet, resolution breach is cleared; if already past due, it remains breached
            if (Instant.now().isBefore(record.getResolutionDueAt())) {
                record.setIsResolutionBreached(false);
            }
            return slaRecordRepository.save(record);
        });
    }

    /**
     * Evaluates SLA breach state at a reference time against deadlines.
     */
    @Transactional
    public SlaRecord evaluateBreaches(SlaRecord record, Instant referenceTime) {
        if (record == null) {
            return null;
        }
        Instant now = referenceTime != null ? referenceTime : Instant.now();

        if (record.getRespondedAt() == null && now.isAfter(record.getResponseDueAt())) {
            record.setIsResponseBreached(true);
        }

        if (record.getResolvedAt() == null && now.isAfter(record.getResolutionDueAt())) {
            record.setIsResolutionBreached(true);
        }

        return slaRecordRepository.save(record);
    }

    /**
     * Scans for and flags all pending SLA records that have exceeded deadlines without response or resolution.
     */
    @Transactional
    public void scanAndEvaluateActiveBreaches(Instant referenceTime) {
        Instant now = referenceTime != null ? referenceTime : Instant.now();

        List<SlaRecord> responseBreaches = slaRecordRepository
                .findByIsResponseBreachedFalseAndRespondedAtIsNullAndResponseDueAtBefore(now);
        for (SlaRecord r : responseBreaches) {
            r.setIsResponseBreached(true);
            if (r.getIncident() != null) {
                notifySlaBreach(r.getIncident(), "Response SLA target has been breached");
            }
        }
        if (!responseBreaches.isEmpty()) {
            slaRecordRepository.saveAll(responseBreaches);
            log.warn("Flagged {} overdue response SLA breaches at {}", responseBreaches.size(), now);
        }

        List<SlaRecord> resolutionBreaches = slaRecordRepository
                .findByIsResolutionBreachedFalseAndResolvedAtIsNullAndResolutionDueAtBefore(now);
        for (SlaRecord r : resolutionBreaches) {
            r.setIsResolutionBreached(true);
            if (r.getIncident() != null) {
                notifySlaBreach(r.getIncident(), "Resolution SLA target has been breached");
            }
        }
        if (!resolutionBreaches.isEmpty()) {
            slaRecordRepository.saveAll(resolutionBreaches);
            log.warn("Flagged {} overdue resolution SLA breaches at {}", resolutionBreaches.size(), now);
        }
    }

    private void notifySlaBreach(Incident incident, String breachType) {
        if (incident == null) {
            return;
        }
        User target = incident.getAssignee() != null ? incident.getAssignee() : incident.getReporter();
        if (target != null) {
            boolean alreadyNotified = notificationRepository.existsByUserIdAndTypeAndReferenceId(
                    target.getId(), NotificationType.SLA_BREACHED.name(), incident.getId());
            if (!alreadyNotified) {
                notificationService.createNotification(
                        target,
                        NotificationType.SLA_BREACHED,
                        "SLA Breached: " + incident.getIncidentNumber(),
                        breachType + " for incident '" + incident.getTitle() + "'.",
                        incident.getId()
                );
            }
        }
    }

    /**
     * Retrieves the SLA record for a specific incident by ID.
     */
    @Transactional(readOnly = true)
    public SlaRecordResponse getSlaRecordByIncidentId(Long incidentId) {
        SlaRecord record = slaRecordRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("SlaRecord for incident", incidentId));
        return SlaRecordResponse.fromEntity(record);
    }

    /**
     * Finds the SLA record entity for an incident.
     */
    @Transactional(readOnly = true)
    public Optional<SlaRecord> findSlaRecordByIncidentId(Long incidentId) {
        return slaRecordRepository.findByIncidentId(incidentId);
    }
}
