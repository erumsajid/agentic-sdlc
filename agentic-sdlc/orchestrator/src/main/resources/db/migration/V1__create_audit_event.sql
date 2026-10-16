-- Append-only audit trail. One row per state transition or governance event.
CREATE TABLE audit_event (
    id           BIGSERIAL PRIMARY KEY,
    run_id       UUID         NOT NULL,
    node_id      VARCHAR(100),
    from_state   VARCHAR(30),
    to_state     VARCHAR(30),
    event_type   VARCHAR(50)  NOT NULL,
    actor        VARCHAR(100) NOT NULL,
    reason       TEXT,
    trace_id     VARCHAR(64)  NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_event_run_time ON audit_event (run_id, occurred_at);
