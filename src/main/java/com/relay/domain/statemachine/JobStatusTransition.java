package com.relay.domain.statemachine;

import com.relay.domain.enums.JobStatus;
import com.relay.exception.IllegalJobStateTransitionException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class JobStatusTransition {

    private static final Map<JobStatus, Set<JobStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(JobStatus.class);

    static {
        // PENDING can move to PROCESSING, CANCELLED, or RETRYING (when circuit breaker is OPEN)
        ALLOWED_TRANSITIONS.put(JobStatus.PENDING, EnumSet.of(JobStatus.PROCESSING, JobStatus.CANCELLED, JobStatus.RETRYING));

        // PROCESSING can complete, fail into RETRYING, or fail into DEAD_LETTER
        ALLOWED_TRANSITIONS.put(JobStatus.PROCESSING, EnumSet.of(JobStatus.COMPLETED, JobStatus.RETRYING, JobStatus.DEAD_LETTER));

        // RETRYING can be picked up to PENDING / PROCESSING, cancelled, moved to DEAD_LETTER, or refreshed in RETRYING
        ALLOWED_TRANSITIONS.put(JobStatus.RETRYING, EnumSet.of(JobStatus.PENDING, JobStatus.PROCESSING, JobStatus.CANCELLED, JobStatus.DEAD_LETTER, JobStatus.RETRYING));

        // DEAD_LETTER can only be replayed back to PENDING
        ALLOWED_TRANSITIONS.put(JobStatus.DEAD_LETTER, EnumSet.of(JobStatus.PENDING));

        // COMPLETED and CANCELLED are terminal states
        ALLOWED_TRANSITIONS.put(JobStatus.COMPLETED, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(JobStatus.CANCELLED, Collections.emptySet());
    }

    private JobStatusTransition() {
    }

    public static boolean isAllowed(JobStatus from, JobStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<JobStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static void validate(JobStatus from, JobStatus to) {
        if (!isAllowed(from, to)) {
            throw new IllegalJobStateTransitionException(from, to);
        }
    }

    public static Set<JobStatus> getAllowedNextStates(JobStatus current) {
        return ALLOWED_TRANSITIONS.getOrDefault(current, Collections.emptySet());
    }
}
