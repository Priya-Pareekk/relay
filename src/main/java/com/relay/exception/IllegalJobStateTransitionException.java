package com.relay.exception;

import com.relay.domain.enums.JobStatus;

public class IllegalJobStateTransitionException extends RuntimeException {

    private final JobStatus fromStatus;
    private final JobStatus toStatus;

    public IllegalJobStateTransitionException(JobStatus fromStatus, JobStatus toStatus) {
        super(String.format("Illegal job status transition from %s to %s", fromStatus, toStatus));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public JobStatus getFromStatus() {
        return fromStatus;
    }

    public JobStatus getToStatus() {
        return toStatus;
    }
}
