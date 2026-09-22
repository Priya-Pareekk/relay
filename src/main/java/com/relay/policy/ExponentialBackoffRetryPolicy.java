package com.relay.policy;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Component
public class ExponentialBackoffRetryPolicy implements RetryPolicy {

    private final long baseDelaySeconds;
    private final long maxDelaySeconds;

    public ExponentialBackoffRetryPolicy(
            @Value("${relay.retry.base-delay-seconds:5}") long baseDelaySeconds,
            @Value("${relay.retry.max-delay-seconds:900}") long maxDelaySeconds) {
        this.baseDelaySeconds = Math.max(1, baseDelaySeconds);
        this.maxDelaySeconds = Math.max(this.baseDelaySeconds, maxDelaySeconds);
    }

    @Override
    public Duration calculateDelay(int attemptNumber) {
        int attempt = Math.max(1, attemptNumber);
        // Calculate: baseDelay * 2^(attempt - 1) with overflow protection
        int exponent = Math.min(attempt - 1, 30);
        long delaySeconds = baseDelaySeconds * (1L << exponent);
        long cappedDelay = Math.min(delaySeconds, maxDelaySeconds);
        return Duration.ofSeconds(cappedDelay);
    }
}
