package com.agenticsdlc.orchestrator.service;

import com.agenticsdlc.orchestrator.entity.AuditEventEntity;
import com.agenticsdlc.orchestrator.entity.NodeExecutionEntity;
import com.agenticsdlc.orchestrator.entity.RunEntity;
import com.agenticsdlc.orchestrator.enums.NodeState;
import com.agenticsdlc.orchestrator.exception.IllegalTransitionException;
import com.agenticsdlc.orchestrator.exception.StateConflictException;
import com.agenticsdlc.orchestrator.repository.AuditEventRepository;
import com.agenticsdlc.orchestrator.repository.NodeExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The only way node state changes. Checks the move is legal, saves the new state
 * and writes the audit event in one transaction.
 */
@Service
public class TransitionService {

    public static final String STATE_TRANSITION = "STATE_TRANSITION";

    @Autowired
    private NodeExecutionRepository nodes;

    @Autowired
    private AuditEventRepository audit;

    @Transactional
    public void transition(RunEntity run, String nodeId, NodeState to, String actor, String reason) {
        NodeExecutionEntity node = nodes.findByRunIdAndNodeId(run.getId(), nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown node '" + nodeId + "' in run " + run.getId()));

        NodeState from = node.getState();
        if (!from.canTransitionTo(to)) {
            throw new IllegalTransitionException(from, to);
        }

        node.setState(to);
        node.setUpdatedAt(Instant.now());
        try {
            nodes.saveAndFlush(node);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new StateConflictException(run.getId(), nodeId);
        }

        audit.save(event(run, nodeId, from, to, STATE_TRANSITION, actor, reason));
    }

    /** Records an audit event that is not a state change. */
    @Transactional
    public void record(RunEntity run, String nodeId, String eventType, String actor, String reason) {
        audit.save(event(run, nodeId, null, null, eventType, actor, reason));
    }

    private AuditEventEntity event(RunEntity run, String nodeId, NodeState from, NodeState to,
                                   String eventType, String actor, String reason) {
        AuditEventEntity event = new AuditEventEntity();
        event.setRunId(run.getId());
        event.setNodeId(nodeId);
        event.setFromState(from);
        event.setToState(to);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setReason(reason);
        event.setTraceId(run.getTraceId());
        event.setOccurredAt(Instant.now());
        return event;
    }
}