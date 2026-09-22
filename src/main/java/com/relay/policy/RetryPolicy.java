package com.relay.policy;

import java.time.Duration;

public interface RetryPolicy {

    /**
     * Calculates the retry delay for a given attempt number.
     *
     * @param attemptNumber the current attempt count (1-based)
     * @return Duration to wait before next retry
     */
    Duration calculateDelay(int attemptNumber);

    /**
     * Calculates the retry delay in seconds.
     *
     * @param attemptNumber the current attempt count (1-based)
     * @return delay in seconds
     */
    default long calculateDelaySeconds(int attemptNumber) {
        return calculateDelay(attemptNumber).toSeconds();
    }
}
