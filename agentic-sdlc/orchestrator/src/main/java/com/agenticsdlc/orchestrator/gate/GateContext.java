package com.agenticsdlc.orchestrator.gate;

import com.agenticsdlc.orchestrator.entity.RunEntity;
import com.agenticsdlc.orchestrator.graph.NodeDefinition;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;

/** What a gate or policy rule can see when it is evaluated. */
@Getter
public class GateContext {

    private final RunEntity run;
    private final NodeDefinition node;
    /** Artifacts already stored for the run: type to content (latest version). */
    private final Map<String, String> available;
    /** What the agent just returned, not yet stored. Empty for entry gates. */
    private final Map<String, String> outputs;

    public GateContext(RunEntity run, NodeDefinition node,
                       Map<String, String> available, Map<String, String> outputs) {
        this.run = run;
        this.node = node;
        this.available = available == null ? Map.of() : Map.copyOf(available);
        this.outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }

    /** The artifact of the given type: this node's fresh output first, then the stored ones. */
    public Optional<String> artifact(String type) {
        if (type == null) {
            return Optional.empty();
        }
        String content = outputs.get(type);
        if (content == null) {
            content = available.get(type);
        }
        return Optional.ofNullable(content);
    }
}