package com.agenticsdlc.orchestrator.exception;

import com.agenticsdlc.orchestrator.enums.NodeState;

public class IllegalTransitionException extends IllegalStateException {

    public IllegalTransitionException(NodeState from, NodeState to) {
        super("Illegal node transition " + from + " -> " + to);
    }
}