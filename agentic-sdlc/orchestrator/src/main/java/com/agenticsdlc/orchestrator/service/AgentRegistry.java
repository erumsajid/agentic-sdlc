package com.agenticsdlc.orchestrator.service;

import com.agenticsdlc.orchestrator.agent.Agent;
import com.agenticsdlc.orchestrator.enums.AgentRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/** Finds the agent for a role. Spring injects the list already sorted by @Order, so the first match wins. */
@Service
public class AgentRegistry {

    @Autowired
    private List<Agent> agents;

    public Agent get(AgentRole role) {
        return agents.stream()
                .filter(agent -> agent.supports(role))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No agent registered for role " + role));
    }
}