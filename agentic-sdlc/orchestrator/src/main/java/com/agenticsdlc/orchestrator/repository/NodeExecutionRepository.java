package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.entity.NodeExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NodeExecutionRepository extends JpaRepository<NodeExecutionEntity, Long> {

    List<NodeExecutionEntity> findByRunId(UUID runId);

    Optional<NodeExecutionEntity> findByRunIdAndNodeId(UUID runId, String nodeId);
}