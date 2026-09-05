package com.resolveai.incident.repository;

import com.resolveai.incident.entity.Incident;
import com.resolveai.incident.entity.IncidentStatus;
import com.resolveai.incident.entity.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {
    Optional<Incident> findByIncidentNumber(String incidentNumber);
    boolean existsByIncidentNumber(String incidentNumber);
    Page<Incident> findByReporterId(Long reporterId, Pageable pageable);
    Page<Incident> findByAssigneeId(Long assigneeId, Pageable pageable);
    Page<Incident> findByTeamId(Long teamId, Pageable pageable);
    List<Incident> findByStatus(IncidentStatus status);
    long countByStatus(IncidentStatus status);
    long countByPriority(Priority priority);
}
