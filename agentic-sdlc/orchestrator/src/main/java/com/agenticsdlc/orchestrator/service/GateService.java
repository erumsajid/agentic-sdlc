package com.agenticsdlc.orchestrator.service;

import com.agenticsdlc.orchestrator.enums.GateOutcome;
import com.agenticsdlc.orchestrator.enums.GateType;
import com.agenticsdlc.orchestrator.gate.GateContext;
import com.agenticsdlc.orchestrator.gate.GateEvaluation;
import com.agenticsdlc.orchestrator.gate.GateEvaluator;
import com.agenticsdlc.orchestrator.gate.GateReport;
import com.agenticsdlc.orchestrator.graph.GateDefinition;
import com.agenticsdlc.orchestrator.graph.NodeDefinition;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a node's gates. Every gate is evaluated (no early exit) so the audit trail shows all problems.
 * Fails closed: a gate type with no evaluator, or an evaluator that throws, is a FAIL.
 */
@Service
public class GateService {

    @Autowired
    private List<GateEvaluator> evaluatorList;

    @Autowired
    private PolicyEngine policyEngine;

    private final Map<GateType, GateEvaluator> evaluators = new EnumMap<>(GateType.class);

    @PostConstruct
    public void index() {
        for (GateEvaluator evaluator : evaluatorList) {
            if (evaluators.put(evaluator.type(), evaluator) != null) {
                throw new IllegalStateException("Two evaluators for gate type " + evaluator.type());
            }
        }
    }

    public List<GateDefinition> entryGates(NodeDefinition node) {
        return node.getEntryGates();
    }

    /**
     * The node's declared exit gates plus a POLICY_CHECK for every global rule the node did not
     * declare itself, so a graph cannot skip a global guardrail by leaving it out.
     */
    public List<GateDefinition> exitGates(NodeDefinition node) {
        List<GateDefinition> gates = new ArrayList<>(node.getExitGates());
        for (String ruleId : policyEngine.globalRuleIds()) {
            boolean declared = node.getExitGates().stream()
                    .anyMatch(g -> g.getType() == GateType.POLICY_CHECK && ruleId.equals(g.getRule()));
            if (!declared) {
                gates.add(new GateDefinition(GateType.POLICY_CHECK, ruleId));
            }
        }
        return gates;
    }

    public GateReport evaluate(List<GateDefinition> gates, GateContext context) {
        List<GateEvaluation> results = new ArrayList<>();
        for (GateDefinition gate : gates) {
            results.add(evaluateOne(gate, context));
        }
        return new GateReport(results);
    }

    private GateEvaluation evaluateOne(GateDefinition gate, GateContext context) {
        GateEvaluator evaluator = evaluators.get(gate.getType());
        if (evaluator == null) {
            return new GateEvaluation(gate, GateOutcome.FAIL, "no evaluator for gate type " + gate.getType());
        }
        try {
            return evaluator.evaluate(gate, context);
        } catch (RuntimeException e) {
            return new GateEvaluation(gate, GateOutcome.FAIL,
                    "evaluator error (fail closed): " + e.getClass().getSimpleName());
        }
    }
}