package com.agenticsdlc.orchestrator.graph;

import com.agenticsdlc.orchestrator.enums.AgentRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class NodeDefinition {
    private String id;
    private String description = "";
    private AgentRole agent;
    private List<String> dependsOn = new ArrayList<>();
    private List<GateDefinition> entryGates = new ArrayList<>();
    private List<GateDefinition> exitGates = new ArrayList<>();
    private int maxRetries;
    private List<String> consumes = new ArrayList<>();
    private List<String> produces = new ArrayList<>();
}