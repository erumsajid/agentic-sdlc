CREATE TABLE run (
    id             UUID PRIMARY KEY,
    graph_name     VARCHAR(100) NOT NULL,
    graph_version  INT          NOT NULL,
    requirement    TEXT         NOT NULL,
    status         VARCHAR(30)  NOT NULL,
    trace_id       VARCHAR(64)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Current state of each node in each run. Changed only through compare-and-set (see JpaRunStore).
CREATE TABLE node_execution (
    run_id      UUID         NOT NULL REFERENCES run (id),
    node_id     VARCHAR(100) NOT NULL,
    state       VARCHAR(30)  NOT NULL,
    attempt     INT          NOT NULL DEFAULT 1,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, node_id)
);

-- Versioned, never overwritten. Lineage (inputs) is added in the lineage phase.
CREATE TABLE artifact (
    id                UUID PRIMARY KEY,
    run_id            UUID         NOT NULL REFERENCES run (id),
    type              VARCHAR(100) NOT NULL,
    version           INT          NOT NULL,
    content_hash      VARCHAR(64)  NOT NULL,
    produced_by_node  VARCHAR(100) NOT NULL,
    content           TEXT         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (run_id, type, version)
);

ALTER TABLE audit_event
    ADD CONSTRAINT fk_audit_event_run FOREIGN KEY (run_id) REFERENCES run (id);
