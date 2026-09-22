package com.relay.repository;

import com.relay.domain.entity.JobArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JobArchiveRepository extends JpaRepository<JobArchive, UUID> {
}
