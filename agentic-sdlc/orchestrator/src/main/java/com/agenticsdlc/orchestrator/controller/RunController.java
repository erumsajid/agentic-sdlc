package com.agenticsdlc.orchestrator.controller;

import com.agenticsdlc.orchestrator.dto.AuditEventResponse;
import com.agenticsdlc.orchestrator.dto.RunResponse;
import com.agenticsdlc.orchestrator.dto.StartRunRequest;
import com.agenticsdlc.orchestrator.dto.StartRunResponse;
import com.agenticsdlc.orchestrator.service.RunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/runs")
public class RunController {

    @Autowired
    private RunService runService;

    @PostMapping
    public ResponseEntity<StartRunResponse> startRun(@RequestBody StartRunRequest request) {
        try {
            UUID runId = runService.startRun(request.getGraph(), request.getRequirement());
            return ResponseEntity.accepted().body(new StartRunResponse(runId));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<RunResponse> getRun(@PathVariable UUID id) {
        return runService.view(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<AuditEventResponse>> getTimeline(@PathVariable UUID id) {
        return runService.timeline(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}