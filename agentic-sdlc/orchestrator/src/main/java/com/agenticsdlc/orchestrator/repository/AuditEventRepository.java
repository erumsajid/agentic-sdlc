package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

    List<AuditEventEntity> findByRunIdOrderByIdAsc(UUID runId);
}