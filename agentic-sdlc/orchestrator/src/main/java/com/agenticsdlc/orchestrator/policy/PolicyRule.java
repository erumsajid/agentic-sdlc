package com.agenticsdlc.orchestrator.policy;

import com.agenticsdlc.orchestrator.gate.GateContext;

public interface PolicyRule {

    String id();

    /** Global rules are applied to every node's output, even if the graph does not mention them. */
    default boolean global() {
        return false;
    }

    PolicyResult evaluate(GateContext context);
}