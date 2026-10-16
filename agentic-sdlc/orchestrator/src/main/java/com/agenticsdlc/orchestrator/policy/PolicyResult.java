package com.agenticsdlc.orchestrator.policy;

import com.agenticsdlc.orchestrator.enums.PolicyDecision;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** The detail goes to the audit log, so it must never contain the content that triggered the rule. */
@Getter
@AllArgsConstructor
public class PolicyResult {

    private final String ruleId;
    private final PolicyDecision decision;
    private final String detail;

    public static PolicyResult allow(String ruleId, String detail) {
        return new PolicyResult(ruleId, PolicyDecision.ALLOW, detail);
    }

    public static PolicyResult warn(String ruleId, String detail) {
        return new PolicyResult(ruleId, PolicyDecision.WARN, detail);
    }

    public static PolicyResult block(String ruleId, String detail) {
        return new PolicyResult(ruleId, PolicyDecision.BLOCK, detail);
    }
}