package com.relay.domain.entity;

import com.relay.domain.enums.AttemptStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_attempt_archive")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobAttemptArchive {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private JobArchive jobArchive;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttemptStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "archived_at", nullable = false)
    @Builder.Default
    private Instant archivedAt = Instant.now();
}
