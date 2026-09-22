package com.relay.domain.statemachine;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.exception.IllegalJobStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobStatusTransitionTest {

    @ParameterizedTest(name = "Valid transition from {0} to {1}")
    @CsvSource({
            "PENDING, PROCESSING",
            "PENDING, CANCELLED",
            "PENDING, RETRYING",
            "PROCESSING, COMPLETED",
            "PROCESSING, RETRYING",
            "PROCESSING, DEAD_LETTER",
            "RETRYING, PENDING",
            "RETRYING, PROCESSING",
            "RETRYING, CANCELLED",
            "RETRYING, DEAD_LETTER",
            "RETRYING, RETRYING",
            "DEAD_LETTER, PENDING"
    })
    void testAllowedTransitions(JobStatus from, JobStatus to) {
        assertThat(JobStatusTransition.isAllowed(from, to)).isTrue();
        assertThatCode(() -> JobStatusTransition.validate(from, to)).doesNotThrowAnyException();

        Job job = Job.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of())
                .status(from)
                .build();

        job.transitionTo(to);
        assertThat(job.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest(name = "Invalid transition from {0} to {1}")
    @CsvSource({
            "COMPLETED, PENDING",
            "COMPLETED, PROCESSING",
            "COMPLETED, RETRYING",
            "COMPLETED, DEAD_LETTER",
            "COMPLETED, CANCELLED",
            "CANCELLED, PENDING",
            "CANCELLED, PROCESSING",
            "CANCELLED, COMPLETED",
            "PROCESSING, PENDING",
            "PROCESSING, CANCELLED",
            "PENDING, COMPLETED",
            "PENDING, DEAD_LETTER",
            "DEAD_LETTER, COMPLETED",
            "DEAD_LETTER, RETRYING",
            "DEAD_LETTER, PROCESSING",
            "DEAD_LETTER, CANCELLED"
    })
    void testDisallowedTransitionsThrowException(JobStatus from, JobStatus to) {
        assertThat(JobStatusTransition.isAllowed(from, to)).isFalse();

        Job job = Job.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of())
                .status(from)
                .build();

        assertThatThrownBy(() -> job.transitionTo(to))
                .isInstanceOf(IllegalJobStateTransitionException.class)
                .hasMessageContaining(String.format("Illegal job status transition from %s to %s", from, to));
    }

    @Test
    @DisplayName("Asserting illegal job transitions produce 409 Conflict exception type")
    void testIllegalReplayAndCancel() {
        // Replaying a COMPLETED job must fail
        Job completedJob = Job.builder().status(JobStatus.COMPLETED).build();
        assertThatThrownBy(() -> completedJob.transitionTo(JobStatus.PENDING))
                .isInstanceOf(IllegalJobStateTransitionException.class);

        // Cancelling a PROCESSING job must fail
        Job processingJob = Job.builder().status(JobStatus.PROCESSING).build();
        assertThatThrownBy(() -> processingJob.transitionTo(JobStatus.CANCELLED))
                .isInstanceOf(IllegalJobStateTransitionException.class);
    }
}
