package com.agenticsdlc.orchestrator.gate;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

/** A gate rule of the form ARTIFACT_TYPE:check-name. Shared by SchemaGate and QualityGate. */
@Getter
@AllArgsConstructor
class TypedRule {

    private final String artifactType;
    private final String checkName;

    static Optional<TypedRule> parse(String rule) {
        if (rule == null) {
            return Optional.empty();
        }
        int colon = rule.indexOf(':');
        if (colon <= 0 || colon == rule.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new TypedRule(rule.substring(0, colon).trim(), rule.substring(colon + 1).trim()));
    }
}