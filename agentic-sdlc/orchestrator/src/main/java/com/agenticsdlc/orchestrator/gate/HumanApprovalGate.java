package com.agenticsdlc.orchestrator.gate;

import com.agenticsdlc.orchestrator.enums.GateOutcome;
import com.agenticsdlc.orchestrator.enums.GateType;
import com.agenticsdlc.orchestrator.graph.GateDefinition;
import org.springframework.stereotype.Component;

/** Placeholder until approval checkpoints are built. Reports NOT_ENFORCED so the gap shows in the audit trail. */
@Component
public class HumanApprovalGate implements GateEvaluator {

    @Override
    public GateType type() {
        return GateType.HUMAN_APPROVAL;
    }

    @Override
    public GateEvaluation evaluate(GateDefinition gate, GateContext context) {
        return new GateEvaluation(gate, GateOutcome.NOT_ENFORCED,
                "approval checkpoints not implemented yet");
    }
}