package com.relay.controller;

import com.relay.dto.JobDefinitionRequest;
import com.relay.dto.JobDefinitionResponse;
import com.relay.dto.JobDefinitionUpdateRequest;
import com.relay.service.JobDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/job-definitions")
@RequiredArgsConstructor
public class JobDefinitionController {

    private final JobDefinitionService jobDefinitionService;

    @GetMapping
    public ResponseEntity<List<JobDefinitionResponse>> getAll() {
        return ResponseEntity.ok(jobDefinitionService.getAllJobDefinitions());
    }

    @PostMapping
    public ResponseEntity<JobDefinitionResponse> create(@Valid @RequestBody JobDefinitionRequest request) {
        JobDefinitionResponse response = jobDefinitionService.createJobDefinition(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobDefinitionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(jobDefinitionService.getJobDefinitionById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<JobDefinitionResponse> update(
            @PathVariable UUID id,
            @RequestBody JobDefinitionUpdateRequest request) {
        return ResponseEntity.ok(jobDefinitionService.updateJobDefinition(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobDefinitionService.deleteJobDefinition(id);
        return ResponseEntity.noContent().build();
    }
}
