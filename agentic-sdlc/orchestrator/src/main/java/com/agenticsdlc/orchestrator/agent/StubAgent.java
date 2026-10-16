package com.agenticsdlc.orchestrator.agent;

import com.agenticsdlc.orchestrator.enums.AgentRole;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Deterministic fallback agent for every role. Lowest precedence, so a real agent
 * that supports the same role (added later with a higher @Order) is preferred.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class StubAgent implements Agent {

    @Override
    public boolean supports(AgentRole role) {
        return true;
    }

    @Override
    public Map<String, String> execute(AgentRole role, String requirement,
                                       Map<String, String> inputs, List<String> expectedArtifacts) {
        Map<String, String> artifacts = new LinkedHashMap<>();
        for (String type : expectedArtifacts) {
            artifacts.put(type, contentFor(type, role, requirement, inputs));
        }
        return artifacts;
    }

    private String contentFor(String type, AgentRole role, String requirement, Map<String, String> inputs) {
        return switch (type) {
            case "TEST_REPORT" -> "tests=12 failed=0 skipped=0";
            case "SCAN_REPORT" -> "findings=0 critical=0 high=0";
            default -> "[stub " + role + "] " + type + " for requirement: " + requirement
                    + " (inputs: " + new TreeSet<>(inputs.keySet()) + ")";
        };
    }
}