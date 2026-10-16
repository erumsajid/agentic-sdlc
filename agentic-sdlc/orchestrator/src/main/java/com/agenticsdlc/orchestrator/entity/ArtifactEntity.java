package com.agenticsdlc.orchestrator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "artifact")
@Getter
@Setter
@NoArgsConstructor
public class ArtifactEntity {

    @Id
    private UUID id;

    private UUID runId;

    private String type;

    /** Artifact version. Not called "version" to avoid confusion with JPA's @Version. */
    private int artifactVersion;

    private String contentHash;

    private String producedByNode;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Instant createdAt;
}