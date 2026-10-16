package com.agenticsdlc.orchestrator.entity;

import com.agenticsdlc.orchestrator.enums.RunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "run")
@Getter
@Setter
@NoArgsConstructor
public class RunEntity {

    @Id
    private UUID id;

    private String graphName;

    private int graphVersion;

    @Column(columnDefinition = "TEXT")
    private String requirement;

    @Enumerated(EnumType.STRING)
    private RunStatus status;

    private String traceId;

    private Instant createdAt;

    private Instant updatedAt;
}