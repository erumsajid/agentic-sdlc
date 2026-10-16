package com.agenticsdlc.orchestrator.enums;

public enum NodeState {
    PENDING, READY, RUNNING, VALIDATING, AWAITING_APPROVAL, SUCCEEDED, FAILED,
    RETRYING, FALLBACK, ROLLED_BACK, REJECTED, BLOCKED, STALE, SKIPPED, SAFE_STOPPED;

    /** The legal transition table lives here, so there is no separate state machine class. */
    public boolean canTransitionTo(NodeState next) {
        return switch (this) {
            case PENDING -> next == READY || next == SKIPPED || next == BLOCKED || next == SAFE_STOPPED;
            case READY -> next == RUNNING || next == BLOCKED || next == SAFE_STOPPED;
            case RUNNING -> next == VALIDATING || next == FAILED || next == SAFE_STOPPED;
            case VALIDATING -> next == AWAITING_APPROVAL || next == SUCCEEDED || next == FAILED
                    || next == REJECTED || next == BLOCKED;
            case AWAITING_APPROVAL -> next == SUCCEEDED || next == REJECTED || next == SAFE_STOPPED;
            case FAILED -> next == RETRYING || next == FALLBACK || next == ROLLED_BACK || next == SAFE_STOPPED;
            case RETRYING, FALLBACK -> next == RUNNING || next == FAILED || next == SAFE_STOPPED;
            case REJECTED -> next == RETRYING || next == ROLLED_BACK || next == SAFE_STOPPED;
            case ROLLED_BACK -> next == PENDING || next == SAFE_STOPPED;
            case BLOCKED -> next == READY || next == SAFE_STOPPED;
            case STALE -> next == PENDING || next == SKIPPED || next == SAFE_STOPPED;
            case SUCCEEDED -> next == STALE;
            case SKIPPED -> false;
            case SAFE_STOPPED -> next == READY;
        };
    }
}