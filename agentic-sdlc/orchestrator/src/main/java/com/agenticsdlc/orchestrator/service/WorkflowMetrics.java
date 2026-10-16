package com.agenticsdlc.orchestrator.service;

import com.agenticsdlc.orchestrator.enums.NodeState;
import com.agenticsdlc.orchestrator.enums.RunStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/** Low-cardinality workflow metrics exported through Actuator's Prometheus endpoint. */
@Component
public class WorkflowMetrics {

    private final MeterRegistry registry;

    public WorkflowMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void runStarted(String graph) {
        counter("orchestrator.runs.started", "graph", graph).increment();
    }

    public void runFinished(String graph, RunStatus status, Duration duration) {
        counter("orchestrator.runs.finished", "graph", graph, "status", label(status.name())).increment();
        if (duration != null && !duration.isNegative()) {
            Timer.builder("orchestrator.run.duration")
                    .tag("graph", graph)
                    .tag("status", label(status.name()))
                    .register(registry)
                    .record(duration);
        }
    }

    public Timer.Sample startAgentAttempt() {
        return Timer.start(registry);
    }

    public void finishAgentAttempt(Timer.Sample sample, String node, String agent,
                                   String outcome) {
        counter("orchestrator.node.attempts", "node", node, "agent", agent,
                "outcome", outcome).increment();
        sample.stop(Timer.builder("orchestrator.node.attempt.duration")
                .tag("node", node)
                .tag("agent", agent)
                .tag("outcome", outcome)
                .register(registry));
    }

    public void nodeTransition(String node, NodeState state) {
        counter("orchestrator.node.transitions", "node", node, "state", label(state.name())).increment();
    }

    public void gateEvaluated(String type, String outcome) {
        counter("orchestrator.gates.evaluated", "type", type, "outcome", label(outcome)).increment();
    }

    public void retry(String node, String reason) {
        counter("orchestrator.retries", "node", node, "reason", reason).increment();
    }

    public void approval(boolean approved) {
        counter("orchestrator.approvals", "decision", approved ? "approved" : "rejected").increment();
    }

    public void rollback(String node) {
        counter("orchestrator.rollbacks", "node", node).increment();
    }

    public void safeStop() {
        Counter.builder("orchestrator.safe.stops").register(registry).increment();
    }

    public void replan(String node) {
        counter("orchestrator.replans", "node", node).increment();
    }

    public void forkBatch(int readyNodes) {
        DistributionSummary.builder("orchestrator.fork.batch.size")
                .register(registry)
                .record(readyNodes);
    }

    public void artifactCreated(String type, String producer) {
        counter("orchestrator.artifacts.created", "type", type, "producer", producer).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private static String label(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
