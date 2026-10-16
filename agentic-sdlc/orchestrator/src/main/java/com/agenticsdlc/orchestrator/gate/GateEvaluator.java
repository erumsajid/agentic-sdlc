package com.agenticsdlc.orchestrator.gate;

import com.agenticsdlc.orchestrator.enums.GateType;
import com.agenticsdlc.orchestrator.graph.GateDefinition;

public interface GateEvaluator {

    GateType type();

    GateEvaluation evaluate(GateDefinition gate, GateContext context);
}