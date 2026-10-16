package com.agenticsdlc.orchestrator.service;

import com.agenticsdlc.orchestrator.dto.AuditEventResponse;
import com.agenticsdlc.orchestrator.dto.ArtifactResponse;
import com.agenticsdlc.orchestrator.dto.RunResponse;
import com.agenticsdlc.orchestrator.entity.NodeExecutionEntity;
import com.agenticsdlc.orchestrator.entity.RunEntity;
import com.agenticsdlc.orchestrator.enums.NodeState;
import com.agenticsdlc.orchestrator.enums.RunStatus;
import com.agenticsdlc.orchestrator.graph.ExecutionGraph;
import com.agenticsdlc.orchestrator.graph.GraphDefinition;
import com.agenticsdlc.orchestrator.repository.AuditEventRepository;
import com.agenticsdlc.orchestrator.repository.ArtifactRepository;
import com.agenticsdlc.orchestrator.repository.NodeExecutionRepository;
import com.agenticsdlc.orchestrator.repository.RunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RunService {

    @Autowired
    private GraphCatalog catalog;

    @Autowired
    private RunRepository runs;

    @Autowired
    private NodeExecutionRepository nodes;

    @Autowired
    private AuditEventRepository audit;

    @Autowired
    private ArtifactRepository artifacts;

    @Autowired
    private WorkflowEngine engine;

    @Autowired
    private WorkflowMetrics metrics;

    /**
     * Creates the run and its PENDING nodes, then starts the engine in the background.
     * Deliberately not @Transactional: the rows must be committed before the async thread looks for them.
     */
    public UUID startRun(String graphName, String requirement) {
        GraphDefinition definition = catalog.find(graphName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown graph '" + graphName + "'"));
        if (requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException("requirement must not be blank");
        }

        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();

        RunEntity run = new RunEntity();
        run.setId(runId);
        run.setGraphName(definition.getName());
        run.setGraphVersion(definition.getVersion());
        run.setRequirement(requirement);
        run.setStatus(RunStatus.RUNNING);
        run.setTraceId(runId.toString().replace("-", ""));
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runs.save(run);
        metrics.runStarted(definition.getName());

        List<NodeExecutionEntity> rows = new ArrayList<>();
        for (String nodeId : new ExecutionGraph(definition).topologicalOrder()) {
            NodeExecutionEntity row = new NodeExecutionEntity();
            row.setRunId(runId);
            row.setNodeId(nodeId);
            row.setState(NodeState.PENDING);
            row.setUpdatedAt(now);
            rows.add(row);
        }
        nodes.saveAll(rows);

        engine.executeAsync(runId);
        return runId;
    }

    public Optional<RunResponse> view(UUID runId) {
        return runs.findById(runId).map(run -> {
            Map<String, NodeState> states = new HashMap<>();
            nodes.findByRunId(runId).forEach(n -> states.put(n.getNodeId(), n.getState()));

            Map<String, NodeState> ordered = new LinkedHashMap<>();
            catalog.find(run.getGraphName()).ifPresentOrElse(
                    definition -> new ExecutionGraph(definition).topologicalOrder()
                            .forEach(id -> ordered.put(id, states.get(id))),
                    () -> ordered.putAll(states));

            return new RunResponse(run.getId(), run.getGraphName(), run.getGraphVersion(), run.getStatus(),
                    run.getRequirement(), run.getTraceId(), run.getCreatedAt(), ordered);
        });
    }

    public Optional<List<AuditEventResponse>> timeline(UUID runId) {
        if (!runs.existsById(runId)) {
            return Optional.empty();
        }
        return Optional.of(audit.findByRunIdOrderByIdAsc(runId).stream()
                .map(AuditEventResponse::from)
                .toList());
    }

    public List<ArtifactResponse> artifacts(UUID runId) {
        if (!runs.existsById(runId)) return null;
        return artifacts.findByRunId(runId).stream().map(ArtifactResponse::from).toList();
    }

    public void decideApproval(UUID runId, String nodeId, boolean approved, String actor, String reason) {
        engine.decideApproval(runId, nodeId, approved, actorOrDefault(actor), reason);
    }

    public void safeStop(UUID runId, String actor, String reason) {
        engine.safeStop(runId, actorOrDefault(actor), reason);
    }

    public void rollback(UUID runId, String nodeId, String actor, String reason) {
        engine.rollback(runId, nodeId, actorOrDefault(actor), reason);
    }

    public void retry(UUID runId, String nodeId, String actor, String reason) {
        engine.retry(runId, nodeId, actorOrDefault(actor), reason);
    }

    public void replan(UUID runId, String nodeId, String actor, String reason) {
        engine.replan(runId, nodeId, actorOrDefault(actor), reason);
    }

    private String actorOrDefault(String actor) {
        return actor == null || actor.isBlank() ? "api-user" : actor;
    }
}
