package com.agenticsdlc.orchestrator.gate;

import com.agenticsdlc.orchestrator.enums.GateType;
import com.agenticsdlc.orchestrator.graph.GateDefinition;
import org.springframework.stereotype.Component;

/** Rule: the artifact type that must exist and not be blank. */
@Component
public class ArtifactExistsGate implements GateEvaluator {

    @Override
    public GateType type() {
        return GateType.ARTIFACT_EXISTS;
    }

    @Override
    public GateEvaluation evaluate(GateDefinition gate, GateContext context) {
        String type = gate.getRule();
        if (type == null || type.isBlank()) {
            return GateEvaluation.fail(gate, "no artifact type given in the rule");
        }
        boolean present = context.artifact(type).filter(content -> !content.isBlank()).isPresent();
        return present
                ? GateEvaluation.pass(gate, "artifact '" + type + "' present")
                : GateEvaluation.fail(gate, "required artifact '" + type + "' is missing or empty");
    }
}