package com.agenticsdlc.orchestrator.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Reads a graph from YAML and validates it. Unknown fields are rejected so typos fail loudly. */
@Component
public class GraphLoader {

    private final ObjectMapper mapper = YAMLMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();

    @Autowired
    private GraphValidator validator;

    public GraphDefinition parse(String yaml) {
        GraphDefinition definition;
        try {
            definition = mapper.readValue(yaml, GraphDefinition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid graph YAML: " + e.getOriginalMessage(), e);
        }
        if (definition == null) {
            throw new IllegalArgumentException("Graph YAML is empty");
        }
        List<String> violations = validator.validate(definition);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Invalid graph definition: " + String.join("; ", violations));
        }
        return definition;
    }

    public GraphDefinition loadFromClasspath(String resource) {
        try (InputStream in = GraphLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Graph resource not found: " + resource);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read graph resource " + resource, e);
        }
    }
}