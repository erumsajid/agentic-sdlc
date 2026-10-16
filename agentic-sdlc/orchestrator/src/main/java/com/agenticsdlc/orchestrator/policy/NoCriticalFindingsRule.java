package com.agenticsdlc.orchestrator.policy;

import com.agenticsdlc.orchestrator.gate.GateContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compliance guardrail on the SCAN_REPORT artifact: critical findings block, high findings warn. */
@Component
public class NoCriticalFindingsRule implements PolicyRule {

    public static final String ID = "no-critical-findings";

    private static final Pattern CRITICAL = Pattern.compile("critical=(\\d+)");
    private static final Pattern HIGH = Pattern.compile("high=(\\d+)");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public PolicyResult evaluate(GateContext context) {
        Optional<String> report = context.artifact("SCAN_REPORT");
        if (report.isEmpty()) {
            return PolicyResult.block(ID, "SCAN_REPORT not found (fail closed)");
        }

        Matcher critical = CRITICAL.matcher(report.get());
        if (!critical.find()) {
            return PolicyResult.block(ID, "SCAN_REPORT has no critical= count (fail closed)");
        }
        long criticalCount = Long.parseLong(critical.group(1));
        if (criticalCount > 0) {
            return PolicyResult.block(ID, criticalCount + " critical finding(s)");
        }

        Matcher high = HIGH.matcher(report.get());
        if (high.find() && Long.parseLong(high.group(1)) > 0) {
            return PolicyResult.warn(ID, high.group(1) + " high finding(s), no critical");
        }
        return PolicyResult.allow(ID, "no critical or high findings");
    }
}