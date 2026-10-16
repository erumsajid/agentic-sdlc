package com.agenticsdlc.orchestrator.gate;

import com.agenticsdlc.orchestrator.enums.GateOutcome;
import com.agenticsdlc.orchestrator.graph.GateDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** The detail goes to the audit log, so it must never contain artifact content. */
@Getter
@AllArgsConstructor
public class GateEvaluation {

    private final GateDefinition gate;
    private final GateOutcome outcome;
    private final String detail;

    public static GateEvaluation pass(GateDefinition gate, String detail) {
        return new GateEvaluation(gate, GateOutcome.PASS, detail);
    }

    public static GateEvaluation fail(GateDefinition gate, String detail) {
        return new GateEvaluation(gate, GateOutcome.FAIL, detail);
    }
}