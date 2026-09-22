package com.relay.service;

import com.relay.domain.entity.JobDefinition;
import com.relay.dto.JobDefinitionRequest;
import com.relay.dto.JobDefinitionResponse;
import com.relay.dto.JobDefinitionUpdateRequest;
import com.relay.exception.ResourceNotFoundException;
import com.relay.repository.JobDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDefinitionService {

    private final JobDefinitionRepository jobDefinitionRepository;

    public static CronExpression parseCronExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Cron expression cannot be empty");
        }
        String normalized = expression.trim();
        String[] parts = normalized.split("\\s+");
        if (parts.length == 5) {
            normalized = "0 " + normalized;
        }
        return CronExpression.parse(normalized);
    }

    @Transactional
    public JobDefinitionResponse createJobDefinition(JobDefinitionRequest request) {
        // Validate cron expression
        parseCronExpression(request.getCronExpression());

        JobDefinition definition = JobDefinition.builder()
                .name(request.getName())
                .jobType(request.getJobType())
                .cronExpression(request.getCronExpression())
                .payloadTemplate(request.getPayloadTemplate())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        JobDefinition saved = jobDefinitionRepository.save(definition);
        log.info("Created JobDefinition: id={}, name={}, cron='{}'",
                saved.getId(), saved.getName(), saved.getCronExpression());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<JobDefinitionResponse> getAllJobDefinitions() {
        return jobDefinitionRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public JobDefinitionResponse getJobDefinitionById(UUID id) {
        JobDefinition definition = jobDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JobDefinition not found with id: " + id));
        return toResponse(definition);
    }

    @Transactional
    public JobDefinitionResponse updateJobDefinition(UUID id, JobDefinitionUpdateRequest request) {
        JobDefinition definition = jobDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JobDefinition not found with id: " + id));

        if (request.getName() != null && !request.getName().isBlank()) {
            definition.setName(request.getName());
        }
        if (request.getJobType() != null) {
            definition.setJobType(request.getJobType());
        }
        if (request.getCronExpression() != null && !request.getCronExpression().isBlank()) {
            parseCronExpression(request.getCronExpression());
            definition.setCronExpression(request.getCronExpression());
        }
        if (request.getPayloadTemplate() != null) {
            definition.setPayloadTemplate(request.getPayloadTemplate());
        }
        if (request.getEnabled() != null) {
            definition.setEnabled(request.getEnabled());
        }

        JobDefinition saved = jobDefinitionRepository.save(definition);
        log.info("Updated JobDefinition: id={}, enabled={}", saved.getId(), saved.isEnabled());
        return toResponse(saved);
    }

    @Transactional
    public void deleteJobDefinition(UUID id) {
        if (!jobDefinitionRepository.existsById(id)) {
            throw new ResourceNotFoundException("JobDefinition not found with id: " + id);
        }
        jobDefinitionRepository.deleteById(id);
        log.info("Deleted JobDefinition: id={}", id);
    }

    public JobDefinitionResponse toResponse(JobDefinition definition) {
        Instant nextExecution = null;
        try {
            CronExpression cron = parseCronExpression(definition.getCronExpression());
            Instant reference = definition.getLastTriggeredAt() != null
                    ? definition.getLastTriggeredAt()
                    : Instant.now();
            ZonedDateTime nextZdt = cron.next(reference.atZone(ZoneOffset.UTC));
            if (nextZdt != null) {
                nextExecution = nextZdt.toInstant();
            }
        } catch (Exception e) {
            log.warn("Could not calculate next execution time for definition id={}: {}", definition.getId(), e.getMessage());
        }

        return JobDefinitionResponse.builder()
                .id(definition.getId())
                .name(definition.getName())
                .jobType(definition.getJobType())
                .cronExpression(definition.getCronExpression())
                .payloadTemplate(definition.getPayloadTemplate())
                .enabled(definition.isEnabled())
                .lastTriggeredAt(definition.getLastTriggeredAt())
                .nextExecutionTime(nextExecution)
                .createdAt(definition.getCreatedAt())
                .updatedAt(definition.getUpdatedAt())
                .build();
    }
}
