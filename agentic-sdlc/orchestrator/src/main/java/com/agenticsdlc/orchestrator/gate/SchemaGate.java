package com.agenticsdlc.orchestrator.gate;

import com.agenticsdlc.orchestrator.enums.GateType;
import com.agenticsdlc.orchestrator.graph.GateDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule: ARTIFACT_TYPE:schema. Built-in schemas: non-blank, json.
 * An unknown schema fails the gate; it is never skipped.
 */
@Component
public class SchemaGate implements GateEvaluator {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public GateType type() {
        return GateType.SCHEMA_VALID;
    }

    @Override
    public GateEvaluation evaluate(GateDefinition gate, GateContext context) {
        Optional<TypedRule> parsed = TypedRule.parse(gate.getRule());
        if (parsed.isEmpty()) {
            return GateEvaluation.fail(gate, "malformed rule (expected ARTIFACT_TYPE:schema)");
        }
        TypedRule rule = parsed.get();

        Optional<String> content = context.artifact(rule.getArtifactType());
        if (content.isEmpty()) {
            return GateEvaluation.fail(gate, "artifact '" + rule.getArtifactType() + "' not found");
        }
        String text = content.get();

        return switch (rule.getCheckName()) {
            case "non-blank" -> text.isBlank()
                    ? GateEvaluation.fail(gate, "artifact '" + rule.getArtifactType() + "' is blank")
                    : GateEvaluation.pass(gate, "artifact '" + rule.getArtifactType() + "' is not blank");
            case "json" -> checkJson(gate, rule, text);
            default -> GateEvaluation.fail(gate, "unknown schema '" + rule.getCheckName() + "' (fail closed)");
        };
    }

    private GateEvaluation checkJson(GateDefinition gate, TypedRule rule, String text) {
        if (text.isBlank()) {
            return GateEvaluation.fail(gate, "artifact '" + rule.getArtifactType() + "' is blank");
        }
        try {
            JSON.readTree(text);
            return GateEvaluation.pass(gate, "artifact '" + rule.getArtifactType() + "' is valid JSON");
        } catch (JsonProcessingException e) {
            return GateEvaluation.fail(gate, "artifact '" + rule.getArtifactType() + "' is not valid JSON");
        }
    }
}