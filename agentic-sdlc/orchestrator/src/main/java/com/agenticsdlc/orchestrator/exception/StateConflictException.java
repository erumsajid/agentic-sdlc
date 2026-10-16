package com.agenticsdlc.orchestrator.exception;

import java.util.UUID;

/** Another thread changed the node first (optimistic lock failure). */
public class StateConflictException extends IllegalStateException {

    public StateConflictException(UUID runId, String nodeId) {
        super("Node '" + nodeId + "' of run " + runId + " was modified concurrently");
    }
}