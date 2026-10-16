package com.agenticsdlc.orchestrator.gate;

import com.agenticsdlc.orchestrator.enums.GateOutcome;
import com.agenticsdlc.orchestrator.enums.GateType;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class GateReport {

    private final List<GateEvaluation> evaluations;

    public GateReport(List<GateEvaluation> evaluations) {
        this.evaluations = List.copyOf(evaluations);
    }

    public boolean blocked() {
        return evaluations.stream().anyMatch(e -> e.getOutcome() == GateOutcome.BLOCK);
    }

    public boolean failed() {
        return evaluations.stream().anyMatch(e -> e.getOutcome() == GateOutcome.FAIL);
    }

    public boolean approvalRequired() {
        return evaluations.stream().anyMatch(e -> e.getGate().getType() == GateType.HUMAN_APPROVAL
                && e.getOutcome() == GateOutcome.BLOCK);
    }

    public boolean hasOtherBlock() {
        return evaluations.stream().anyMatch(e -> e.getOutcome() == GateOutcome.BLOCK
                && e.getGate().getType() != GateType.HUMAN_APPROVAL);
    }

    /** The gates that failed or blocked, as one readable line. */
    public String problems() {
        return evaluations.stream()
                .filter(e -> e.getOutcome() == GateOutcome.BLOCK || e.getOutcome() == GateOutcome.FAIL)
                .map(e -> e.getGate().getType() + ":" + e.getGate().getRule()
                        + " -> " + e.getOutcome() + " (" + e.getDetail() + ")")
                .collect(Collectors.joining("; "));
    }
}
