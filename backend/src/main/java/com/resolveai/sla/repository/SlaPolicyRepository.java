package com.resolveai.sla.repository;

import com.resolveai.incident.entity.Priority;
import com.resolveai.sla.entity.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {
    Optional<SlaPolicy> findByPriority(Priority priority);
    boolean existsByPriority(Priority priority);
    List<SlaPolicy> findByIsActiveTrue();
}
