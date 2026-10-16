package com.agenticsdlc.orchestrator.graph;

import com.agenticsdlc.orchestrator.enums.GateType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GateDefinition {
    private GateType type;
    private String rule;
}