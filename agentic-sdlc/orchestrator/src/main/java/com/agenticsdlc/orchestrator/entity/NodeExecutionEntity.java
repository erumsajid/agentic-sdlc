package com.agenticsdlc.orchestrator.entity;

import com.agenticsdlc.orchestrator.enums.NodeState;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "node_execution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"runId", "nodeId"}))
@Getter
@Setter
@NoArgsConstructor
public class NodeExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID runId;

    private String nodeId;

    @Enumerated(EnumType.STRING)
    private NodeState state;

    private int attempt = 1;

    /** Optimistic locking: a concurrent change fails the save instead of overwriting. */
    @Version
    private Long version;

    private Instant updatedAt;
}