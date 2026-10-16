package com.agenticsdlc.orchestrator.service;

import com.agenticsdlc.orchestrator.graph.GraphDefinition;
import com.agenticsdlc.orchestrator.graph.GraphLoader;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The graphs available to start runs with. Loaded and validated once at startup from classpath graphs/*.yaml. */
@Service
public class GraphCatalog {

    @Autowired
    private GraphLoader loader;

    private final Map<String, GraphDefinition> graphs = new LinkedHashMap<>();

    @PostConstruct
    public void loadAll() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:graphs/*.yaml");
        for (Resource resource : resources) {
            String yaml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            GraphDefinition definition = loader.parse(yaml);
            if (graphs.put(definition.getName(), definition) != null) {
                throw new IllegalStateException("Two graph files define the name '" + definition.getName() + "'");
            }
        }
    }

    public Optional<GraphDefinition> find(String name) {
        return Optional.ofNullable(graphs.get(name));
    }

    public Set<String> names() {
        return graphs.keySet();
    }
}