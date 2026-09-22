package com.relay.repository;

import com.relay.domain.entity.JobDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobDefinitionRepository extends JpaRepository<JobDefinition, UUID> {
    List<JobDefinition> findByEnabledTrue();
}
