package com.agenticsdlc.orchestrator.exception;

import java.util.List;

/** Thrown when a graph definition cannot be parsed or breaks a structural rule. */
public class GraphValidationException extends RuntimeException {

    private final List<String> violations;

    public GraphValidationException(List<String> violations) {
        super("Invalid graph definition: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
