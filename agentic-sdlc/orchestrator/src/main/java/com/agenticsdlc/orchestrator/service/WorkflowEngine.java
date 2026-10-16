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
import io.micrometer.core.instrument.Timer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
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
 * Supports approval checkpoints, retries, rollback, safe-stop and lineage-aware replanning.
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

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowMetrics metrics;

    @Async
    public void executeAsync(UUID runId) {
        try {
            execute(runId);
        } catch (RuntimeException e) {
            log.error("Run {} aborted by an unexpected error", runId, e);
            runs.findById(runId).ifPresent(run -> finish(run,
                    run.isStopRequested() ? RunStatus.SAFE_STOPPED : RunStatus.FAILED));
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
            RunEntity currentRun = runs.findById(runId).orElseThrow();
            if (currentRun.isStopRequested()) {
                finish(currentRun, RunStatus.SAFE_STOPPED);
                return;
            }
            Set<String> succeeded = new LinkedHashSet<>();
            Set<String> excluded = new LinkedHashSet<>();
            boolean anyBlocked = false;
            boolean awaitingApproval = false;
            for (NodeExecutionEntity node : nodes.findByRunId(runId)) {
                if (node.getState() == NodeState.SUCCEEDED) {
                    succeeded.add(node.getNodeId());
                } else if (node.getState() != NodeState.PENDING && node.getState() != NodeState.READY) {
                    excluded.add(node.getNodeId());
                    if (node.getState() == NodeState.BLOCKED) {
                        anyBlocked = true;
                    }
                    if (node.getState() == NodeState.AWAITING_APPROVAL) {
                        awaitingApproval = true;
                    }
                }
            }

            if (succeeded.containsAll(allNodes)) {
                finish(currentRun, RunStatus.SUCCEEDED);
                return;
            }

            List<String> ready = graph.readyNodes(succeeded, excluded);
            if (ready.isEmpty()) {
                finish(currentRun, awaitingApproval ? RunStatus.WAITING_APPROVAL
                        : anyBlocked ? RunStatus.BLOCKED : RunStatus.FAILED);
                return;
            }
            // All nodes in this snapshot have satisfied their dependencies. Run them as
            // one forked batch, then recompute readiness only after every branch finishes.
            metrics.forkBatch(ready.size());
            CompletableFuture<?>[] branches = ready.stream()
                    .map(nodeId -> CompletableFuture.runAsync(() -> runNode(currentRun, graph.node(nodeId))))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(branches).join();
        }
    }

    private void runNode(RunEntity run, NodeDefinition node) {
        UUID runId = run.getId();
        String id = node.getId();

        NodeExecutionEntity current = nodes.findByRunIdAndNodeId(runId, id).orElseThrow();
        if (current.getState() != NodeState.PENDING && current.getState() != NodeState.READY
                && current.getState() != NodeState.RUNNING) return;
        if (runs.findById(runId).orElseThrow().isStopRequested()) {
            if (current.getState() != NodeState.SAFE_STOPPED) {
                transitions.transition(run, id, NodeState.SAFE_STOPPED, ENGINE, "safe stop requested");
            }
            return;
        }

        if (current.getState() == NodeState.PENDING) {
            transitions.transition(run, id, NodeState.READY, ENGINE, "all dependencies succeeded");
            current = nodes.findByRunIdAndNodeId(runId, id).orElseThrow();
        }
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

        if (current.getState() != NodeState.RUNNING) {
            transitions.transition(run, id, NodeState.RUNNING, ENGINE, "dispatched to " + node.getAgent());
        }

        Map<String, String> outputs = executeWithRetries(run, node, inputs);
        if (outputs == null) {
            return;
        }

        if (runs.findById(runId).orElseThrow().isStopRequested()) {
            transitions.transition(run, id, NodeState.SAFE_STOPPED, ENGINE, "safe stop requested");
            return;
        }

        transitions.transition(run, id, NodeState.VALIDATING, "agent:" + node.getAgent(),
                "produced " + node.getProduces());

        // exit gates run on everything the agent returned; nothing is stored yet
        GateReport exit = gateService.evaluate(gateService.exitGates(node),
                new GateContext(run, node, available, outputs));
        recordEvaluations(run, id, "exit", exit);
        if (exit.blocked()) {
            if (exit.approvalRequired() && !exit.hasOtherBlock() && !exit.failed()) {
                NodeExecutionEntity execution = nodes.findByRunIdAndNodeId(runId, id).orElseThrow();
                try {
                    execution.setPendingOutputs(objectMapper.writeValueAsString(outputs));
                    nodes.save(execution);
                } catch (Exception e) {
                    throw new IllegalStateException("Could not persist outputs pending approval", e);
                }
                transitions.transition(run, id, NodeState.AWAITING_APPROVAL, ENGINE,
                        "waiting for human approval: " + exit.problems());
                return;
            }
            transitions.transition(run, id, NodeState.BLOCKED, ENGINE, "exit gates blocked: " + exit.problems());
            return;
        }
        if (exit.failed()) {
            transitions.transition(run, id, NodeState.FAILED, ENGINE, "exit gates failed: " + exit.problems());
            NodeExecutionEntity failed = nodes.findByRunIdAndNodeId(runId, id).orElseThrow();
            if (failed.getAttempt() <= node.getMaxRetries()) {
                metrics.retry(id, "exit_gate");
                transitions.transition(run, id, NodeState.RETRYING, ENGINE,
                        "retrying after exit gate failure on attempt " + failed.getAttempt());
                NodeExecutionEntity retry = nodes.findByRunIdAndNodeId(runId, id).orElseThrow();
                retry.setAttempt(retry.getAttempt() + 1);
                nodes.save(retry);
                transitions.transition(run, id, NodeState.RUNNING, ENGINE, "retry attempt " + retry.getAttempt());
                runNode(run, node);
            }
            return;
        }

        if (runs.findById(runId).orElseThrow().isStopRequested()) {
            transitions.transition(run, id, NodeState.SAFE_STOPPED, ENGINE, "safe stop requested");
            return;
        }

        // only validated outputs are stored
        for (String type : node.getProduces()) {
            saveArtifact(runId, type, outputs.get(type), id, node.getConsumes());
        }
        transitions.transition(run, id, NodeState.SUCCEEDED, ENGINE, "exit gates passed");
    }

    /** Runs an agent again after transient failures, bounded by the node's maxRetries. */
    private Map<String, String> executeWithRetries(RunEntity run, NodeDefinition node, Map<String, String> inputs) {
        String id = node.getId();
        while (true) {
            String failure = null;
            Map<String, String> outputs = null;
            Timer.Sample sample = metrics.startAgentAttempt();
            String outcome = "failure";
            try {
                outputs = agentRegistry.get(node.getAgent())
                        .execute(node.getAgent(), run.getRequirement(), inputs, node.getProduces());
                Map<String, String> candidate = outputs;
                List<String> missing = node.getProduces().stream().filter(type -> !candidate.containsKey(type)).toList();
                if (!missing.isEmpty()) {
                    failure = "agent did not produce declared artifacts " + missing;
                } else {
                    outcome = "success";
                }
            } catch (RuntimeException e) {
                failure = "agent failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                metrics.finishAgentAttempt(sample, id, node.getAgent().name().toLowerCase(), outcome);
            }
            if (failure == null) return outputs;

            NodeExecutionEntity execution = nodes.findByRunIdAndNodeId(run.getId(), id).orElseThrow();
            log.warn("Node {} of run {} attempt {} failed: {}", id, run.getId(), execution.getAttempt(), failure);
            transitions.transition(run, id, NodeState.FAILED, ENGINE, failure);
            if (execution.getAttempt() > node.getMaxRetries()) return null;
            metrics.retry(id, "agent_failure");
            transitions.transition(run, id, NodeState.RETRYING, ENGINE, "retrying after attempt " + execution.getAttempt());
            NodeExecutionEntity retry = nodes.findByRunIdAndNodeId(run.getId(), id).orElseThrow();
            retry.setAttempt(retry.getAttempt() + 1);
            nodes.save(retry);
            transitions.transition(run, id, NodeState.RUNNING, ENGINE, "retry attempt " + retry.getAttempt());
        }
    }

    public void decideApproval(UUID runId, String nodeId, boolean approved, String actor, String reason) {
        RunEntity run = runs.findById(runId).orElseThrow(() -> new IllegalArgumentException("Unknown run " + runId));
        NodeExecutionEntity execution = nodes.findByRunIdAndNodeId(runId, nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown node " + nodeId));
        if (execution.getState() != NodeState.AWAITING_APPROVAL) {
            throw new IllegalStateException("Node is not awaiting approval");
        }
        metrics.approval(approved);
        if (!approved) {
            transitions.transition(run, nodeId, NodeState.REJECTED, actor,
                    reason == null || reason.isBlank() ? "approval rejected" : reason);
            finish(run, RunStatus.BLOCKED);
            return;
        }
        Map<String, String> outputs;
        try {
            outputs = objectMapper.readValue(execution.getPendingOutputs(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not read outputs pending approval", e);
        }
        GraphDefinition definition = catalog.find(run.getGraphName()).orElseThrow();
        NodeDefinition node = new ExecutionGraph(definition).node(nodeId);
        for (String type : node.getProduces()) {
            saveArtifact(runId, type, outputs.get(type), nodeId, node.getConsumes());
        }
        execution.setPendingOutputs(null);
        nodes.save(execution);
        transitions.transition(run, nodeId, NodeState.SUCCEEDED, actor,
                reason == null || reason.isBlank() ? "approval granted" : reason);
        run.setStatus(RunStatus.RUNNING);
        run.setUpdatedAt(Instant.now());
        runs.save(run);
        CompletableFuture.runAsync(() -> executeAsync(runId));
    }

    public void retry(UUID runId, String nodeId, String actor, String reason) {
        RunEntity run = runs.findById(runId).orElseThrow(() -> new IllegalArgumentException("Unknown run " + runId));
        NodeExecutionEntity execution = nodes.findByRunIdAndNodeId(runId, nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown node " + nodeId));
        if (run.getStatus() == RunStatus.RUNNING || run.getStatus() == RunStatus.WAITING_APPROVAL) {
            throw new IllegalStateException("Stop the active run before retrying a node");
        }
        if (run.getStatus() == RunStatus.SAFE_STOPPED) {
            for (NodeExecutionEntity stopped : nodes.findByRunId(runId)) {
                if (stopped.getState() == NodeState.SAFE_STOPPED) {
                    transitions.transition(run, stopped.getNodeId(), NodeState.READY, actor,
                            "resuming after safe stop");
                }
            }
            execution = nodes.findByRunIdAndNodeId(runId, nodeId).orElseThrow();
        }
        String why = reason == null || reason.isBlank() ? "manual retry requested" : reason;
        if (execution.getState() == NodeState.FAILED || execution.getState() == NodeState.REJECTED) {
            metrics.retry(nodeId, "manual");
            transitions.transition(run, nodeId, NodeState.RETRYING, actor, why);
            NodeExecutionEntity retry = nodes.findByRunIdAndNodeId(runId, nodeId).orElseThrow();
            retry.setAttempt(retry.getAttempt() + 1);
            nodes.save(retry);
            transitions.transition(run, nodeId, NodeState.RUNNING, actor, "manual retry attempt " + retry.getAttempt());
        } else if (execution.getState() == NodeState.BLOCKED || execution.getState() == NodeState.SAFE_STOPPED) {
            transitions.transition(run, nodeId, NodeState.READY, actor, why);
        } else if (execution.getState() == NodeState.READY) {
            transitions.record(run, nodeId, "RETRY_REQUESTED", actor, why);
        } else {
            throw new IllegalStateException("Node must be failed, rejected, blocked, or safe-stopped to retry");
        }
        NodeExecutionEntity reset = nodes.findByRunIdAndNodeId(runId, nodeId).orElseThrow();
        reset.setPendingOutputs(null);
        nodes.save(reset);
        run.setStopRequested(false);
        run.setStatus(RunStatus.RUNNING);
        run.setUpdatedAt(Instant.now());
        runs.save(run);
        GraphDefinition definition = catalog.find(run.getGraphName()).orElseThrow();
        NodeDefinition retryNode = new ExecutionGraph(definition).node(nodeId);
        CompletableFuture.runAsync(() -> retryNode(runId, retryNode));
    }

    private void retryNode(UUID runId, NodeDefinition node) {
        try {
            RunEntity run = runs.findById(runId).orElseThrow();
            runNode(run, node);
            execute(runId);
        } catch (RuntimeException e) {
            log.error("Manual retry for node {} of run {} aborted", node.getId(), runId, e);
            runs.findById(runId).ifPresent(run -> finish(run,
                    run.isStopRequested() ? RunStatus.SAFE_STOPPED : RunStatus.FAILED));
        }
    }

    public void safeStop(UUID runId, String actor, String reason) {
        RunEntity run = runs.findById(runId).orElseThrow(() -> new IllegalArgumentException("Unknown run " + runId));
        if (run.getStatus() != RunStatus.RUNNING && run.getStatus() != RunStatus.WAITING_APPROVAL) {
            throw new IllegalStateException("Only a running or approval-waiting run can be safe-stopped");
        }
        run.setStopRequested(true);
        run.setStatus(RunStatus.SAFE_STOPPED);
        run.setUpdatedAt(Instant.now());
        runs.save(run);
        metrics.safeStop();
        for (NodeExecutionEntity node : nodes.findByRunId(runId)) {
            if (node.getState() == NodeState.PENDING || node.getState() == NodeState.READY
                    || node.getState() == NodeState.AWAITING_APPROVAL) {
                transitions.transition(run, node.getNodeId(), NodeState.SAFE_STOPPED, actor,
                        reason == null || reason.isBlank() ? "safe stop requested" : reason);
            }
        }
        transitions.record(run, null, "SAFE_STOP_REQUESTED", actor,
                reason == null || reason.isBlank() ? "safe stop requested" : reason);
    }

    public void rollback(UUID runId, String nodeId, String actor, String reason) {
        RunEntity run = runs.findById(runId).orElseThrow(() -> new IllegalArgumentException("Unknown run " + runId));
        requireTerminalRun(run);
        GraphDefinition definition = catalog.find(run.getGraphName()).orElseThrow();
        ExecutionGraph graph = new ExecutionGraph(definition);
        NodeExecutionEntity target = nodes.findByRunIdAndNodeId(runId, nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown node " + nodeId));
        if (target.getState() != NodeState.SUCCEEDED) {
            throw new IllegalStateException("Only a succeeded node can be rolled back");
        }
        metrics.rollback(nodeId);
        String auditReason = reason == null || reason.isBlank() ? "rollback requested" : reason;
        transitions.transition(run, nodeId, NodeState.ROLLED_BACK, actor, auditReason);
        deactivateOutputsAndRestorePrevious(runId, nodeId);
        for (String dependent : graph.downstreamOf(nodeId)) {
            NodeExecutionEntity execution = nodes.findByRunIdAndNodeId(runId, dependent).orElseThrow();
            if (execution.getState() == NodeState.SUCCEEDED) {
                transitions.transition(run, dependent, NodeState.STALE, actor,
                        "upstream node " + nodeId + " was rolled back");
                deactivateOutputsAndRestorePrevious(runId, dependent);
            }
        }
        finish(run, RunStatus.BLOCKED);
        transitions.record(run, nodeId, "ROLLBACK_COMPLETED", actor, auditReason);
    }

    private void deactivateOutputsAndRestorePrevious(UUID runId, String nodeId) {
        for (ArtifactEntity artifact : artifacts.findByRunIdAndProducedByNodeAndActiveTrue(runId, nodeId)) {
            artifact.setActive(false);
            artifacts.save(artifact);
            artifacts.findTopByRunIdAndTypeAndArtifactVersionLessThanOrderByArtifactVersionDesc(
                    runId, artifact.getType(), artifact.getArtifactVersion()).ifPresent(previous -> {
                previous.setActive(true);
                artifacts.save(previous);
            });
        }
    }

    public void replan(UUID runId, String nodeId, String actor, String reason) {
        RunEntity run = runs.findById(runId).orElseThrow(() -> new IllegalArgumentException("Unknown run " + runId));
        requireTerminalRun(run);
        GraphDefinition definition = catalog.find(run.getGraphName()).orElseThrow();
        ExecutionGraph graph = new ExecutionGraph(definition);
        NodeExecutionEntity target = nodes.findByRunIdAndNodeId(runId, nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown node " + nodeId));
        if (target.getState() != NodeState.SUCCEEDED && target.getState() != NodeState.ROLLED_BACK) {
            throw new IllegalStateException("Only a succeeded or rolled-back node can be replanned");
        }
        Set<String> affected = new LinkedHashSet<>();
        affected.add(nodeId);
        affected.addAll(graph.downstreamOf(nodeId));
        for (String id : affected) {
            NodeState state = nodes.findByRunIdAndNodeId(runId, id).orElseThrow().getState();
            boolean valid = id.equals(nodeId)
                    ? state == NodeState.SUCCEEDED || state == NodeState.ROLLED_BACK
                    : state == NodeState.SUCCEEDED || state == NodeState.STALE;
            if (!valid) throw new IllegalStateException("Node " + id + " is not in a re-plannable state: " + state);
        }
        for (String id : graph.topologicalOrder()) {
            if (!affected.contains(id)) continue;
            NodeExecutionEntity execution = nodes.findByRunIdAndNodeId(runId, id).orElseThrow();
            if (id.equals(nodeId) && execution.getState() == NodeState.ROLLED_BACK) {
                transitions.transition(run, id, NodeState.PENDING, actor, "replanned after rollback");
            } else {
                if (execution.getState() == NodeState.SUCCEEDED) {
                    transitions.transition(run, id, NodeState.STALE, actor, "upstream replan invalidated this result");
                }
                NodeExecutionEntity stale = nodes.findByRunIdAndNodeId(runId, id).orElseThrow();
                if (stale.getState() == NodeState.STALE) {
                    transitions.transition(run, id, NodeState.PENDING, actor, "scheduled for re-planning");
                } else if (!id.equals(nodeId)) {
                    throw new IllegalStateException("Dependent node " + id + " is not in a re-plannable state");
                }
            }
        }
        run.setStopRequested(false);
        run.setStatus(RunStatus.RUNNING);
        run.setUpdatedAt(Instant.now());
        runs.save(run);
        metrics.replan(nodeId);
        transitions.record(run, nodeId, "REPLAN_REQUESTED", actor,
                reason == null || reason.isBlank() ? "node and downstream lineage scheduled again" : reason);
        CompletableFuture.runAsync(() -> executeAsync(runId));
    }

    private void requireTerminalRun(RunEntity run) {
        if (run.getStatus() == RunStatus.RUNNING || run.getStatus() == RunStatus.WAITING_APPROVAL) {
            throw new IllegalStateException("Run must be stopped before rollback or replanning");
        }
    }

    private void recordEvaluations(RunEntity run, String nodeId, String stage, GateReport report) {
        for (GateEvaluation evaluation : report.getEvaluations()) {
            metrics.gateEvaluated(evaluation.getGate().getType().name(), evaluation.getOutcome().name());
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
        for (ArtifactEntity artifact : artifacts.findByRunIdAndActiveTrue(runId)) {
            ArtifactEntity current = latest.get(artifact.getType());
            if (current == null || artifact.getArtifactVersion() > current.getArtifactVersion()) {
                latest.put(artifact.getType(), artifact);
            }
        }
        Map<String, String> contents = new LinkedHashMap<>();
        latest.forEach((type, artifact) -> contents.put(type, artifact.getContent()));
        return contents;
    }

    private void saveArtifact(UUID runId, String type, String content, String producedByNode, List<String> inputTypes) {
        Map<String, Map<String, Object>> lineage = new LinkedHashMap<>();
        for (String inputType : inputTypes) {
            artifacts.findByRunIdAndActiveTrue(runId).stream()
                    .filter(input -> input.getType().equals(inputType))
                    .max(java.util.Comparator.comparingInt(ArtifactEntity::getArtifactVersion))
                    .ifPresent(input -> lineage.put(inputType, Map.of("artifactId", input.getId().toString(),
                            "version", input.getArtifactVersion(), "contentHash", input.getContentHash())));
        }
        String lineageJson;
        try {
            lineageJson = objectMapper.writeValueAsString(lineage);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize artifact lineage", e);
        }
        artifacts.findByRunIdAndActiveTrue(runId).stream()
                .filter(existing -> existing.getType().equals(type))
                .forEach(existing -> { existing.setActive(false); artifacts.save(existing); });
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
        artifact.setLineageJson(lineageJson);
        artifact.setContent(content);
        artifact.setCreatedAt(Instant.now());
        artifacts.save(artifact);
        metrics.artifactCreated(type, producedByNode);
    }

    private void finish(RunEntity run, RunStatus status) {
        run.setStatus(status);
        Instant finishedAt = Instant.now();
        run.setUpdatedAt(finishedAt);
        runs.save(run);
        if (status != RunStatus.WAITING_APPROVAL) {
            Duration duration = run.getCreatedAt() == null ? null
                    : Duration.between(run.getCreatedAt(), finishedAt);
            metrics.runFinished(run.getGraphName(), status, duration);
        }
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
