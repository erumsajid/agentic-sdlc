package com.agenticsdlc.orchestrator.graph;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Queryable view of a validated graph. Edges point from a dependency to its dependent. */
public class ExecutionGraph {

    private final GraphDefinition definition;
    private final Graph<String, DefaultEdge> graph;
    private final Map<String, NodeDefinition> nodesById = new HashMap<>();

    public ExecutionGraph(GraphDefinition definition) {
        this.definition = definition;
        this.graph = buildGraph(definition);
        definition.getNodes().forEach(n -> nodesById.put(n.getId(), n));
    }

    public static Graph<String, DefaultEdge> buildGraph(GraphDefinition definition) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (NodeDefinition node : definition.getNodes()) {
            graph.addVertex(node.getId());
        }
        for (NodeDefinition node : definition.getNodes()) {
            for (String dependency : node.getDependsOn()) {
                if (graph.containsVertex(dependency) && !dependency.equals(node.getId())) {
                    graph.addEdge(dependency, node.getId());
                }
            }
        }
        return graph;
    }

    /** Everything reachable from start, excluding start itself. */
    public static Set<String> reachable(Graph<String, DefaultEdge> graph, String start) {
        Set<String> result = new LinkedHashSet<>();
        BreadthFirstIterator<String, DefaultEdge> iterator = new BreadthFirstIterator<>(graph, start);
        while (iterator.hasNext()) {
            String vertex = iterator.next();
            if (!vertex.equals(start)) {
                result.add(vertex);
            }
        }
        return result;
    }

    public GraphDefinition getDefinition() {
        return definition;
    }

    public NodeDefinition node(String id) {
        NodeDefinition node = nodesById.get(id);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node '" + id + "'");
        }
        return node;
    }

    public List<String> topologicalOrder() {
        List<String> order = new ArrayList<>();
        new TopologicalOrderIterator<>(graph).forEachRemaining(order::add);
        return order;
    }

    /** Nodes whose dependencies all succeeded and that are neither done nor excluded by the caller. */
    public List<String> readyNodes(Set<String> succeeded, Set<String> excluded) {
        List<String> ready = new ArrayList<>();
        for (String id : topologicalOrder()) {
            if (succeeded.contains(id) || excluded.contains(id)) {
                continue;
            }
            if (succeeded.containsAll(Graphs.predecessorListOf(graph, id))) {
                ready.add(id);
            }
        }
        return ready;
    }

    /** Every node that transitively depends on id (these become STALE if id's output changes). */
    public Set<String> downstreamOf(String id) {
        node(id);
        return reachable(graph, id);
    }
}