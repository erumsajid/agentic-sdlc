package com.agenticsdlc.orchestrator.dto;

import com.agenticsdlc.orchestrator.enums.NodeState;
import com.agenticsdlc.orchestrator.enums.RunStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RunResponse {
    private UUID id;
    private String graph;
    private int graphVersion;
    private RunStatus status;
    private String requirement;
    private String traceId;
    private Instant createdAt;
    private Map<String, NodeState> nodes;   // in graph order
}