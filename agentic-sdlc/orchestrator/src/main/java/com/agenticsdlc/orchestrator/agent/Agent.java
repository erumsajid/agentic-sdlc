package com.agenticsdlc.orchestrator.agent;

import com.agenticsdlc.orchestrator.enums.AgentRole;

import java.util.List;
import java.util.Map;

public interface Agent {

    boolean supports(AgentRole role);

    /**
     * @param inputs            only the artifacts the node declares in consumes (type to content)
     * @param expectedArtifacts the artifact types the node must produce
     * @return artifact type to content. Throw a RuntimeException on failure.
     */
    Map<String, String> execute(AgentRole role, String requirement,
                                Map<String, String> inputs, List<String> expectedArtifacts);
}