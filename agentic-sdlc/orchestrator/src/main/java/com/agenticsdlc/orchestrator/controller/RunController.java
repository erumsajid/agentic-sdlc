package com.agenticsdlc.orchestrator.controller;

import com.agenticsdlc.orchestrator.dto.AuditEventResponse;
import com.agenticsdlc.orchestrator.dto.ApprovalDecisionRequest;
import com.agenticsdlc.orchestrator.dto.ArtifactResponse;
import com.agenticsdlc.orchestrator.dto.ControlRequest;
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

    @GetMapping("/{id}/artifacts")
    public ResponseEntity<List<ArtifactResponse>> getArtifacts(@PathVariable UUID id) {
        List<ArtifactResponse> result = runService.artifacts(id);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/nodes/{nodeId}/approval")
    public ResponseEntity<Void> decideApproval(@PathVariable UUID id, @PathVariable String nodeId,
                                               @RequestBody ApprovalDecisionRequest request) {
        if (request.getApproved() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "approved is required");
        }
        try {
            runService.decideApproval(id, nodeId, request.getApproved(), request.getActor(), request.getReason());
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{id}/safe-stop")
    public ResponseEntity<Void> safeStop(@PathVariable UUID id, @RequestBody(required = false) ControlRequest request) {
        try {
            runService.safeStop(id, request == null ? null : request.getActor(),
                    request == null ? null : request.getReason());
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{id}/nodes/{nodeId}/rollback")
    public ResponseEntity<Void> rollback(@PathVariable UUID id, @PathVariable String nodeId,
                                         @RequestBody(required = false) ControlRequest request) {
        try {
            runService.rollback(id, nodeId, request == null ? null : request.getActor(),
                    request == null ? null : request.getReason());
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{id}/nodes/{nodeId}/retry")
    public ResponseEntity<Void> retry(@PathVariable UUID id, @PathVariable String nodeId,
                                      @RequestBody(required = false) ControlRequest request) {
        try {
            runService.retry(id, nodeId, request == null ? null : request.getActor(),
                    request == null ? null : request.getReason());
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{id}/nodes/{nodeId}/replan")
    public ResponseEntity<Void> replan(@PathVariable UUID id, @PathVariable String nodeId,
                                       @RequestBody(required = false) ControlRequest request) {
        try {
            runService.replan(id, nodeId, request == null ? null : request.getActor(),
                    request == null ? null : request.getReason());
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
