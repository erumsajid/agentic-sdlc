package com.agenticsdlc.orchestrator.dto;

import com.agenticsdlc.orchestrator.entity.AuditEventEntity;
import com.agenticsdlc.orchestrator.enums.NodeState;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class AuditEventResponse {
    private String nodeId;
    private NodeState fromState;
    private NodeState toState;
    private String eventType;
    private String actor;
    private String reason;
    private String traceId;
    private Instant occurredAt;

    public static AuditEventResponse from(AuditEventEntity entity) {
        AuditEventResponse response = new AuditEventResponse();
        response.setNodeId(entity.getNodeId());
        response.setFromState(entity.getFromState());
        response.setToState(entity.getToState());
        response.setEventType(entity.getEventType());
        response.setActor(entity.getActor());
        response.setReason(entity.getReason());
        response.setTraceId(entity.getTraceId());
        response.setOccurredAt(entity.getOccurredAt());
        return response;
    }
}