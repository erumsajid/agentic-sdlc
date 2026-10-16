package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.entity.RunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RunRepository extends JpaRepository<RunEntity, UUID> {
}