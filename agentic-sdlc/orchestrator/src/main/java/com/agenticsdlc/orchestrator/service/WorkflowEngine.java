package com.agenticsdlc.orchestrator.service;

import com.agenticsdlc.orchestrator.entity.ArtifactEntity;
import com.agenticsdlc.orchestrator.entity.NodeExecutionEntity;
import com.agenticsdlc.orchestrator.entity.RunEntity;
import com.agenticsdlc.orchestrator.enums.NodeState;
import com.agenticsdlc.orchestrator.enums.RunStatus;
import com.agenticsdlc.orchestrator.gate.GateContext;
import com.agenticsdlc.orchestrator.gate.GateEvaluation;
import com.agenticsdlc.orchestrator.gate.GateReport;
import com.agenticsdlc.orchestrator.graph.ExecutionGraph;
import com.agenticsdlc.orchestrator.graph.GraphDefinition;
import com.agenticsdlc.orchestrator.graph.NodeDefinition;
import com.agenticsdlc.orchestrator.repository.ArtifactRepository;
import com.agenticsdlc.orchestrator.repository.NodeExecutionRepository;
import com.agenticsdlc.orchestrator.repository.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Graph engine with gates and policy checks. Independent ready nodes execute concurrently.
 * Not built yet: approvals, retry, rollback, resume.
 */
@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final String ENGINE = "engine";
    private static final String GATE_EVALUATED = "GATE_EVALUATED";

    @Autowired
    private RunRepository runs;

    @Autowired
    private NodeExecutionRepository nodes;

    @Autowired
    private ArtifactRepository artifacts;

    @Autowired
    private GraphCatalog catalog;

    @Autowired
    private AgentRegistry agentRegistry;

    @Autowired
    private TransitionService transitions;

    @Autowired
    private GateService gateService;

    @Async
    public void executeAsync(UUID runId) {
        try {
            execute(runId);
        } catch (RuntimeException e) {
            log.error("Run {} aborted by an unexpected error", runId, e);
            runs.findById(runId).ifPresent(run -> finish(run, RunStatus.FAILED));
        }
    }

    public void execute(UUID runId) {
        RunEntity run = runs.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown run " + runId));
        GraphDefinition definition = catalog.find(run.getGraphName())
                .orElseThrow(() -> new IllegalStateException("Graph '" + run.getGraphName() + "' is no longer available"));
        ExecutionGraph graph = new ExecutionGraph(definition);
        Set<String> allNodes = new LinkedHashSet<>(graph.topologicalOrder());

        while (true) {
            Set<String> succeeded = new LinkedHashSet<>();
            Set<String> excluded = new LinkedHashSet<>();
            boolean anyBlocked = false;
            for (NodeExecutionEntity node : nodes.findByRunId(runId)) {
                if (node.getState() == NodeState.SUCCEEDED) {
                    succeeded.add(node.getNodeId());
                } else if (node.getState() != NodeState.PENDING) {
                    excluded.add(node.getNodeId());
                    if (node.getState() == NodeState.BLOCKED) {
                        anyBlocked = true;
                    }
                }
            }

            if (succeeded.containsAll(allNodes)) {
                finish(run, RunStatus.SUCCEEDED);
                return;
            }

            List<String> ready = graph.readyNodes(succeeded, excluded);
            if (ready.isEmpty()) {
                finish(run, anyBlocked ? RunStatus.BLOCKED : RunStatus.FAILED);
                return;
            }
            // All nodes in this snapshot have satisfied their dependencies. Run them as
            // one forked batch, then recompute readiness only after every branch finishes.
            CompletableFuture<?>[] branches = ready.stream()
                    .map(nodeId -> CompletableFuture.runAsync(() -> runNode(run, graph.node(nodeId))))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(branches).join();
        }
    }

    private void runNode(RunEntity run, NodeDefinition node) {
        UUID runId = run.getId();
        String id = node.getId();

        transitions.transition(run, id, NodeState.READY, ENGINE, "all dependencies succeeded");
        Map<String, String> available = latestArtifacts(runId);

        // entry gates
        GateReport entry = gateService.evaluate(gateService.entryGates(node),
                new GateContext(run, node, available, Map.of()));
        recordEvaluations(run, id, "entry", entry);
        if (entry.blocked() || entry.failed()) {
            transitions.transition(run, id, NodeState.BLOCKED, ENGINE,
                    "entry gates not satisfied: " + entry.problems());
            return;
        }

        // inputs: only the artifacts this node declares in consumes
        Map<String, String> inputs = new LinkedHashMap<>();
        List<String> missingInputs = new ArrayList<>();
        for (String type : node.getConsumes()) {
            if (available.containsKey(type)) {
                inputs.put(type, available.get(type));
            } else {
                missingInputs.add(type);
            }
        }
        if (!missingInputs.isEmpty()) {
            transitions.transition(run, id, NodeState.BLOCKED, ENGINE,
                    "missing input artifacts " + missingInputs);
            return;
        }

        transitions.transition(run, id, NodeState.RUNNING, ENGINE, "dispatched to " + node.getAgent());

        Map<String, String> outputs;
        try {
            outputs = agentRegistry.get(node.getAgent())
                    .execute(node.getAgent(), run.getRequirement(), inputs, node.getProduces());
        } catch (RuntimeException e) {
            failNode(run, id, "agent failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        List<String> missingOutputs = new ArrayList<>();
        for (String type : node.getProduces()) {
            if (!outputs.containsKey(type)) {
                missingOutputs.add(type);
            }
        }
        if (!missingOutputs.isEmpty()) {
            failNode(run, id, "agent did not produce declared artifacts " + missingOutputs);
            return;
        }

        transitions.transition(run, id, NodeState.VALIDATING, "agent:" + node.getAgent(),
                "produced " + node.getProduces());

        // exit gates run on everything the agent returned; nothing is stored yet
        GateReport exit = gateService.evaluate(gateService.exitGates(node),
                new GateContext(run, node, available, outputs));
        recordEvaluations(run, id, "exit", exit);
        if (exit.blocked()) {
            transitions.transition(run, id, NodeState.BLOCKED, ENGINE, "exit gates blocked: " + exit.problems());
            return;
        }
        if (exit.failed()) {
            transitions.transition(run, id, NodeState.FAILED, ENGINE, "exit gates failed: " + exit.problems());
            return;
        }

        // only validated outputs are stored
        for (String type : node.getProduces()) {
            saveArtifact(runId, type, outputs.get(type), id);
        }
        transitions.transition(run, id, NodeState.SUCCEEDED, ENGINE, "exit gates passed");
    }

    private void recordEvaluations(RunEntity run, String nodeId, String stage, GateReport report) {
        for (GateEvaluation evaluation : report.getEvaluations()) {
            transitions.record(run, nodeId, GATE_EVALUATED, ENGINE,
                    stage + " " + evaluation.getGate().getType() + ":" + evaluation.getGate().getRule()
                            + " -> " + evaluation.getOutcome() + ": " + evaluation.getDetail());
        }
    }

    private void failNode(RunEntity run, String nodeId, String reason) {
        log.warn("Node {} of run {} failed: {}", nodeId, run.getId(), reason);
        transitions.transition(run, nodeId, NodeState.FAILED, ENGINE, reason);
    }

    /** Latest version of each artifact type in the run: type to content. */
    private Map<String, String> latestArtifacts(UUID runId) {
        Map<String, ArtifactEntity> latest = new HashMap<>();
        for (ArtifactEntity artifact : artifacts.findByRunId(runId)) {
            ArtifactEntity current = latest.get(artifact.getType());
            if (current == null || artifact.getArtifactVersion() > current.getArtifactVersion()) {
                latest.put(artifact.getType(), artifact);
            }
        }
        Map<String, String> contents = new LinkedHashMap<>();
        latest.forEach((type, artifact) -> contents.put(type, artifact.getContent()));
        return contents;
    }

    private void saveArtifact(UUID runId, String type, String content, String producedByNode) {
        int nextVersion = artifacts.findTopByRunIdAndTypeOrderByArtifactVersionDesc(runId, type)
                .map(latest -> latest.getArtifactVersion() + 1)
                .orElse(1);

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(UUID.randomUUID());
        artifact.setRunId(runId);
        artifact.setType(type);
        artifact.setArtifactVersion(nextVersion);
        artifact.setContentHash(sha256(content));
        artifact.setProducedByNode(producedByNode);
        artifact.setContent(content);
        artifact.setCreatedAt(Instant.now());
        artifacts.save(artifact);
    }

    private void finish(RunEntity run, RunStatus status) {
        run.setStatus(status);
        run.setUpdatedAt(Instant.now());
        runs.save(run);
        log.info("Run {} finished with status {}", run.getId(), status);
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
