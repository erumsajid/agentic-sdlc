package com.agenticsdlc.orchestrator.policy;

import com.agenticsdlc.orchestrator.gate.GateContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Security guardrail: blocks output that looks like it contains credentials. Never reports the matched text. */
@Component
public class NoSecretsRule implements PolicyRule {

    public static final String ID = "no-secrets";

    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        PATTERNS.put("aws-access-key-id", Pattern.compile("AKIA[0-9A-Z]{16}"));
        PATTERNS.put("private-key-block",
                Pattern.compile("-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"));
        PATTERNS.put("credential-assignment",
                Pattern.compile("(?i)(?:password|passwd|secret|api[_-]?key|token)\\s*[:=]\\s*['\"]?[^\\s'\"]{8,}"));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean global() {
        return true;
    }

    @Override
    public PolicyResult evaluate(GateContext context) {
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, String> output : context.getOutputs().entrySet()) {
            for (Map.Entry<String, Pattern> pattern : PATTERNS.entrySet()) {
                if (pattern.getValue().matcher(output.getValue()).find()) {
                    hits.add(output.getKey() + " (" + pattern.getKey() + ")");
                }
            }
        }
        if (hits.isEmpty()) {
            return PolicyResult.allow(ID, "no secret patterns in " + context.getOutputs().size() + " output artifact(s)");
        }
        return PolicyResult.block(ID, "possible secrets in: " + String.join(", ", hits));
    }
}