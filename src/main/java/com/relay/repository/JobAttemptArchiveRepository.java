package com.relay.repository;

import com.relay.domain.entity.JobAttemptArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JobAttemptArchiveRepository extends JpaRepository<JobAttemptArchive, UUID> {
}
