package com.agenticsdlc.orchestrator.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ControlRequest {
    private String actor;
    private String reason;
}
