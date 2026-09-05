package com.resolveai.incident.repository;

import com.resolveai.incident.entity.IncidentComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentCommentRepository extends JpaRepository<IncidentComment, Long> {
    List<IncidentComment> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
    List<IncidentComment> findByIncidentIdAndIsInternalFalseOrderByCreatedAtAsc(Long incidentId);
}
