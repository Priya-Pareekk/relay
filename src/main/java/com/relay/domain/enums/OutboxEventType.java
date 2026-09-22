package com.relay.domain.enums;

public enum OutboxEventType {
    JOB_SUBMITTED,
    RETRY_REQUEUE,
    JOB_RETRY,
    DEAD_LETTER,
    JOB_COMPLETED
}

