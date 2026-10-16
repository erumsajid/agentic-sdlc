package com.agenticsdlc.orchestrator.gate;

import com.agenticsdlc.orchestrator.enums.GateType;
import com.agenticsdlc.orchestrator.graph.GateDefinition;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule: ARTIFACT_TYPE:check. Built-in checks:
 * tests-passed (report has tests=N failed=M with N > 0 and M = 0) and
 * no-todo-markers (no TODO or FIXME). An unknown check fails the gate.
 */
@Component
public class QualityGate implements GateEvaluator {

    private static final Pattern TESTS = Pattern.compile("tests=(\\d+)");
    private static final Pattern FAILED = Pattern.compile("failed=(\\d+)");
    private static final Pattern TODO = Pattern.compile("\\b(?:TODO|FIXME)\\b");

    @Override
    public GateType type() {
        return GateType.QUALITY_CHECK;
    }

    @Override
    public GateEvaluation evaluate(GateDefinition gate, GateContext context) {
        Optional<TypedRule> parsed = TypedRule.parse(gate.getRule());
        if (parsed.isEmpty()) {
            return GateEvaluation.fail(gate, "malformed rule (expected ARTIFACT_TYPE:check)");
        }
        TypedRule rule = parsed.get();

        Optional<String> content = context.artifact(rule.getArtifactType());
        if (content.isEmpty()) {
            return GateEvaluation.fail(gate, "artifact '" + rule.getArtifactType() + "' not found");
        }

        return switch (rule.getCheckName()) {
            case "tests-passed" -> testsPassed(gate, content.get());
            case "no-todo-markers" -> TODO.matcher(content.get()).find()
                    ? GateEvaluation.fail(gate, "artifact '" + rule.getArtifactType() + "' contains TODO/FIXME markers")
                    : GateEvaluation.pass(gate, "no TODO/FIXME markers");
            default -> GateEvaluation.fail(gate, "unknown quality check '" + rule.getCheckName() + "' (fail closed)");
        };
    }

    private GateEvaluation testsPassed(GateDefinition gate, String report) {
        Matcher tests = TESTS.matcher(report);
        Matcher failed = FAILED.matcher(report);
        if (!tests.find() || !failed.find()) {
            return GateEvaluation.fail(gate, "report has no tests=/failed= counts");
        }

        long total;
        long failures;
        try {
            total = Long.parseLong(tests.group(1));
            failures = Long.parseLong(failed.group(1));
        } catch (NumberFormatException e) {
            return GateEvaluation.fail(gate, "report counts are unreadable");
        }

        if (total == 0) {
            return GateEvaluation.fail(gate, "no tests were run");
        }
        if (failures > 0) {
            return GateEvaluation.fail(gate, failures + " of " + total + " tests failed");
        }
        return GateEvaluation.pass(gate, total + " tests passed");
    }
}