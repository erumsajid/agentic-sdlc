package com.agenticsdlc.orchestrator.gate;

import com.agenticsdlc.orchestrator.enums.GateOutcome;
import com.agenticsdlc.orchestrator.enums.GateType;
import com.agenticsdlc.orchestrator.graph.GateDefinition;
import org.springframework.stereotype.Component;

/** Approval is a blocking checkpoint; the run pauses until an explicit decision arrives. */
@Component
public class HumanApprovalGate implements GateEvaluator {

    @Override
    public GateType type() {
        return GateType.HUMAN_APPROVAL;
    }

    @Override
    public GateEvaluation evaluate(GateDefinition gate, GateContext context) {
        return new GateEvaluation(gate, GateOutcome.BLOCK, "human approval required");
    }
}
