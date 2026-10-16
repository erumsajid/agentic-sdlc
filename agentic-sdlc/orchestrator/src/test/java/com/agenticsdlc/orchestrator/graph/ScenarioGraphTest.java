package com.agenticsdlc.orchestrator.graph;

import com.agenticsdlc.orchestrator.enums.GateType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioGraphTest {
    private final GraphLoader loader = new GraphLoader(new GraphValidator());

    @Test
    void allThreeAssignmentScenariosLoadAsValidGraphs() {
        Map<String, GraphDefinition> graphs = Map.of(
                "greenfield-skeleton", loader.loadFromClasspath("graphs/greenfield-skeleton.yaml"),
                "brownfield-modernization", loader.loadFromClasspath("graphs/brownfield-modernization.yaml"),
                "ambiguous-requirement", loader.loadFromClasspath("graphs/ambiguous-requirement.yaml"));

        assertThat(graphs.keySet()).containsExactlyInAnyOrder(
                "greenfield-skeleton", "brownfield-modernization", "ambiguous-requirement");
        graphs.values().forEach(graph -> assertThat(graph.getNodes()).isNotEmpty());
    }

    @Test
    void brownfieldScenarioPerformsDiscoveryAndJoinsAllReviewBranches() {
        GraphDefinition graph = loader.loadFromClasspath("graphs/brownfield-modernization.yaml");
        Map<String, NodeDefinition> nodes = byId(graph);

        assertThat(nodes.get("REQUIREMENTS").getConsumes()).contains("CURRENT_STATE", "COMPONENT_MAP");
        assertThat(nodes.get("DESIGN").getExitGates())
                .anySatisfy(gate -> assertThat(gate.getType()).isEqualTo(GateType.HUMAN_APPROVAL));
        assertThat(nodes.get("RELEASE_READINESS").getDependsOn())
                .containsExactlyInAnyOrder("IMPLEMENT", "TEST_PLAN", "SECURITY_REVIEW");
    }

    @Test
    void ambiguousScenarioRequiresApprovalBeforeRequirementsAndDesign() {
        GraphDefinition graph = loader.loadFromClasspath("graphs/ambiguous-requirement.yaml");
        Map<String, NodeDefinition> nodes = byId(graph);

        assertThat(nodes.get("CLARIFY_SCOPE").getProduces()).contains("CLARIFICATION_BRIEF", "ASSUMPTIONS");
        assertThat(nodes.get("CLARIFY_SCOPE").getExitGates())
                .anySatisfy(gate -> assertThat(gate.getType()).isEqualTo(GateType.HUMAN_APPROVAL));
        assertThat(nodes.get("REQUIREMENTS").getDependsOn()).containsExactly("CLARIFY_SCOPE");
        assertThat(nodes.get("DESIGN").getExitGates())
                .anySatisfy(gate -> assertThat(gate.getType()).isEqualTo(GateType.HUMAN_APPROVAL));
    }

    @Test
    void greenfieldScenarioRetainsParallelForkAndJoin() {
        GraphDefinition graph = loader.loadFromClasspath("graphs/greenfield-skeleton.yaml");
        Map<String, NodeDefinition> nodes = byId(graph);

        assertThat(nodes.get("IMPLEMENT").getDependsOn()).contains("DESIGN");
        assertThat(nodes.get("TEST_PLAN").getDependsOn()).contains("DESIGN");
        assertThat(nodes.get("RELEASE_READINESS").getDependsOn())
                .containsExactlyInAnyOrder("IMPLEMENT", "TEST_PLAN");
    }

    private Map<String, NodeDefinition> byId(GraphDefinition graph) {
        return graph.getNodes().stream().collect(Collectors.toMap(NodeDefinition::getId, Function.identity()));
    }
}
