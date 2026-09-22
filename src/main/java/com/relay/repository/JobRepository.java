package com.relay.repository;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT j FROM Job j LEFT JOIN FETCH j.attempts WHERE j.id = :id")
    Optional<Job> findByIdWithAttempts(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT j FROM Job j WHERE j.status = :status AND j.nextRetryAt <= :now ORDER BY j.nextRetryAt ASC")
    List<Job> findRetryableJobs(@Param("status") JobStatus status, @Param("now") Instant now, Pageable pageable);

    long countByStatus(JobStatus status);

    @Query("SELECT COUNT(j) FROM Job j WHERE j.status = :status AND j.createdAt >= :since")
    long countByStatusSince(@Param("status") JobStatus status, @Param("since") Instant since);

    @Query("SELECT COUNT(j) FROM Job j WHERE j.createdAt >= :since")
    long countTotalSince(@Param("since") Instant since);

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    Page<Job> findByJobType(JobType jobType, Pageable pageable);

    Page<Job> findByJobTypeAndStatus(JobType jobType, JobStatus status, Pageable pageable);

    @Query("SELECT DISTINCT j FROM Job j LEFT JOIN FETCH j.attempts WHERE j.status = :status AND j.updatedAt < :cutoff ORDER BY j.updatedAt ASC")
    List<Job> findCompletedJobsOlderThan(@Param("status") JobStatus status, @Param("cutoff") Instant cutoff, Pageable pageable);

}
