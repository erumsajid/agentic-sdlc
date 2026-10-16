package com.agenticsdlc.orchestrator.gate;

import com.agenticsdlc.orchestrator.enums.GateOutcome;
import com.agenticsdlc.orchestrator.enums.GateType;
import com.agenticsdlc.orchestrator.graph.GateDefinition;
import com.agenticsdlc.orchestrator.policy.PolicyResult;
import com.agenticsdlc.orchestrator.service.PolicyEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Rule: the id of a policy rule. ALLOW passes, WARN passes with a warning, BLOCK blocks the node. */
@Component
public class PolicyGate implements GateEvaluator {

    @Autowired
    private PolicyEngine policyEngine;

    @Override
    public GateType type() {
        return GateType.POLICY_CHECK;
    }

    @Override
    public GateEvaluation evaluate(GateDefinition gate, GateContext context) {
        PolicyResult result = policyEngine.evaluate(gate.getRule(), context);
        GateOutcome outcome = switch (result.getDecision()) {
            case ALLOW -> GateOutcome.PASS;
            case WARN -> GateOutcome.WARN;
            case BLOCK -> GateOutcome.BLOCK;
        };
        return new GateEvaluation(gate, outcome, result.getDetail());
    }
}