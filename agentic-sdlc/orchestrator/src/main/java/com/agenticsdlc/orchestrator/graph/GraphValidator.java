package com.agenticsdlc.orchestrator.graph;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.EdgeReversedGraph;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Structural checks on a graph definition. Reports every violation found, not just the first. */
@Component
public class GraphValidator {

    public List<String> validate(GraphDefinition definition) {
        List<String> violations = new ArrayList<>();

        if (definition.getName() == null || definition.getName().isBlank()) {
            violations.add("graph name is required");
        }
        if (definition.getNodes() == null || definition.getNodes().isEmpty()) {
            violations.add("graph has no nodes");
            return violations;
        }
        if (checkNodes(definition, violations)) {
            return violations;   // graph analysis needs consistent ids and dependencies
        }

        Graph<String, DefaultEdge> graph = ExecutionGraph.buildGraph(definition);

        CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(graph);
        boolean cyclic = cycleDetector.detectCycles();
        if (cyclic) {
            violations.add("cycle detected among nodes " + new TreeSet<>(cycleDetector.findCycles()));
        }

        ConnectivityInspector<String, DefaultEdge> inspector = new ConnectivityInspector<>(graph);
        if (!inspector.isConnected()) {
            List<Set<String>> components = new ArrayList<>();
            for (Set<String> component : inspector.connectedSets()) {
                components.add(new TreeSet<>(component));
            }
            violations.add("graph is disconnected, separate components: " + components);
        }

        if (!cyclic) {
            checkArtifactFlow(definition, graph, violations);
        }
        return violations;
    }

    /** Returns true if ids or dependencies are inconsistent. */
    private boolean checkNodes(GraphDefinition definition, List<String> violations) {
        boolean structuralError = false;
        Set<String> ids = new LinkedHashSet<>();

        for (NodeDefinition node : definition.getNodes()) {
            if (node.getId() == null || node.getId().isBlank()) {
                violations.add("a node has no id");
                structuralError = true;
            } else if (!ids.add(node.getId())) {
                violations.add("duplicate node id '" + node.getId() + "'");
                structuralError = true;
            }
        }

        for (NodeDefinition node : definition.getNodes()) {
            if (node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            if (node.getAgent() == null) {
                violations.add("node '" + node.getId() + "' has no agent");
            }
            if (node.getMaxRetries() < 0) {
                violations.add("node '" + node.getId() + "' has a negative maxRetries");
            }
            for (String dependency : node.getDependsOn()) {
                if (dependency.equals(node.getId())) {
                    violations.add("node '" + node.getId() + "' depends on itself");
                    structuralError = true;
                } else if (!ids.contains(dependency)) {
                    violations.add("node '" + node.getId() + "' depends on unknown node '" + dependency + "'");
                    structuralError = true;
                }
            }
        }
        return structuralError;
    }

    /** Every artifact a node consumes must be produced by one of its upstream nodes. */
    private void checkArtifactFlow(GraphDefinition definition, Graph<String, DefaultEdge> graph,
                                   List<String> violations) {
        Map<String, NodeDefinition> byId = definition.getNodes().stream()
                .collect(Collectors.toMap(NodeDefinition::getId, Function.identity()));
        Graph<String, DefaultEdge> reversed = new EdgeReversedGraph<>(graph);

        for (NodeDefinition node : definition.getNodes()) {
            if (node.getConsumes().isEmpty()) {
                continue;
            }
            Set<String> available = new HashSet<>();
            for (String ancestor : ExecutionGraph.reachable(reversed, node.getId())) {
                available.addAll(byId.get(ancestor).getProduces());
            }
            for (String needed : node.getConsumes()) {
                if (!available.contains(needed)) {
                    violations.add("node '" + node.getId() + "' consumes artifact '" + needed
                            + "' that no upstream node produces");
                }
            }
        }
    }
}