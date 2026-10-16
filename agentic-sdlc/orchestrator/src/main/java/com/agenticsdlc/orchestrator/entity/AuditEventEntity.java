package com.agenticsdlc.orchestrator.entity;

import com.agenticsdlc.orchestrator.enums.NodeState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit trail: rows are inserted, never updated. */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID runId;

    private String nodeId;

    @Enumerated(EnumType.STRING)
    private NodeState fromState;

    @Enumerated(EnumType.STRING)
    private NodeState toState;

    private String eventType;

    private String actor;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private String traceId;

    private Instant occurredAt;
}