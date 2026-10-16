ALTER TABLE run ADD COLUMN stop_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE node_execution ADD COLUMN pending_outputs TEXT;
ALTER TABLE artifact ADD COLUMN lineage_json TEXT;
ALTER TABLE artifact ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- Captures exact input artifact identity and version for each produced artifact.
CREATE INDEX idx_artifact_run_type_active_version
    ON artifact (run_id, type, active, version DESC);
