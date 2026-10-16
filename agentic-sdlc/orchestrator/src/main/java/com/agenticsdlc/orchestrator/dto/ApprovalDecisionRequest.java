package com.agenticsdlc.orchestrator.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApprovalDecisionRequest {
    private Boolean approved;
    private String actor;
    private String reason;
}
