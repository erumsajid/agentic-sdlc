package com.agenticsdlc.orchestrator.service;

import com.agenticsdlc.orchestrator.gate.GateContext;
import com.agenticsdlc.orchestrator.policy.PolicyResult;
import com.agenticsdlc.orchestrator.policy.PolicyRule;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluates policy rules by id. Fails closed: an unknown rule or a rule that throws is a BLOCK. */
@Service
public class PolicyEngine {

    @Autowired
    private List<PolicyRule> rules;

    private final Map<String, PolicyRule> byId = new LinkedHashMap<>();

    @PostConstruct
    public void index() {
        for (PolicyRule rule : rules) {
            if (byId.put(rule.id(), rule) != null) {
                throw new IllegalStateException("Two policy rules share the id '" + rule.id() + "'");
            }
        }
    }

    public PolicyResult evaluate(String ruleId, GateContext context) {
        PolicyRule rule = byId.get(ruleId);
        if (rule == null) {
            return PolicyResult.block(ruleId, "unknown policy rule (fail closed)");
        }
        try {
            return rule.evaluate(context);
        } catch (RuntimeException e) {
            return PolicyResult.block(ruleId, "rule error (fail closed): " + e.getClass().getSimpleName());
        }
    }

    public List<String> globalRuleIds() {
        return byId.values().stream().filter(PolicyRule::global).map(PolicyRule::id).toList();
    }
}