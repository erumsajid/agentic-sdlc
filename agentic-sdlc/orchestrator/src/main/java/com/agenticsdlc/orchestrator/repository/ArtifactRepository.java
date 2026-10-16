package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArtifactRepository extends JpaRepository<ArtifactEntity, UUID> {

    Optional<ArtifactEntity> findTopByRunIdAndTypeOrderByArtifactVersionDesc(UUID runId, String type);

    List<ArtifactEntity> findByRunId(UUID runId);

    List<ArtifactEntity> findByRunIdAndActiveTrue(UUID runId);

    List<ArtifactEntity> findByRunIdAndProducedByNodeAndActiveTrue(UUID runId, String producedByNode);

    Optional<ArtifactEntity> findTopByRunIdAndTypeAndArtifactVersionLessThanOrderByArtifactVersionDesc(
            UUID runId, String type, int artifactVersion);
}
