package com.agenticsdlc.orchestrator.dto;

import com.agenticsdlc.orchestrator.entity.ArtifactEntity;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class ArtifactResponse {
    private final UUID id;
    private final String type;
    private final int version;
    private final String contentHash;
    private final String producedByNode;
    private final boolean active;
    private final String lineageJson;
    private final Instant createdAt;

    private ArtifactResponse(ArtifactEntity artifact) {
        this.id = artifact.getId();
        this.type = artifact.getType();
        this.version = artifact.getArtifactVersion();
        this.contentHash = artifact.getContentHash();
        this.producedByNode = artifact.getProducedByNode();
        this.active = artifact.isActive();
        this.lineageJson = artifact.getLineageJson();
        this.createdAt = artifact.getCreatedAt();
    }

    public static ArtifactResponse from(ArtifactEntity artifact) {
        return new ArtifactResponse(artifact);
    }
}
