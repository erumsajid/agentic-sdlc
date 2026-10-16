package com.agenticsdlc.orchestrator.graph;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
public class GraphDefinition {
    private String name;
    private String description = "";
    private int version = 1;
    private List<NodeDefinition> nodes = new ArrayList<>();

    public Optional<NodeDefinition> findNode(String id) {
        return nodes.stream().filter(n -> n.getId() != null && n.getId().equals(id)).findFirst();
    }
}