package com.relay.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialBackoffRetryPolicyTest {

    @Test
    @DisplayName("Should correctly calculate exponential backoff sequence")
    void testExponentialBackoffSequence() {
        // base = 5s, max = 900s
        RetryPolicy policy = new ExponentialBackoffRetryPolicy(5L, 900L);

        // Attempt 1: 5 * 2^0 = 5s
        assertThat(policy.calculateDelay(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.calculateDelaySeconds(1)).isEqualTo(5);

        // Attempt 2: 5 * 2^1 = 10s
        assertThat(policy.calculateDelay(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.calculateDelaySeconds(2)).isEqualTo(10);

        // Attempt 3: 5 * 2^2 = 20s
        assertThat(policy.calculateDelay(3)).isEqualTo(Duration.ofSeconds(20));

        // Attempt 4: 5 * 2^3 = 40s
        assertThat(policy.calculateDelay(4)).isEqualTo(Duration.ofSeconds(40));

        // Attempt 5: 5 * 2^4 = 80s
        assertThat(policy.calculateDelay(5)).isEqualTo(Duration.ofSeconds(80));
    }

    @Test
    @DisplayName("Should cap exponential delay at maxDelaySeconds")
    void testExponentialBackoffMaxDelayCap() {
        // base = 10s, max = 50s
        RetryPolicy policy = new ExponentialBackoffRetryPolicy(10L, 50L);

        // Attempt 1: 10s
        assertThat(policy.calculateDelaySeconds(1)).isEqualTo(10);
        // Attempt 2: 20s
        assertThat(policy.calculateDelaySeconds(2)).isEqualTo(20);
        // Attempt 3: 40s
        assertThat(policy.calculateDelaySeconds(3)).isEqualTo(40);
        // Attempt 4: 80s -> capped at 50s
        assertThat(policy.calculateDelaySeconds(4)).isEqualTo(50);
        // Attempt 10: huge -> capped at 50s
        assertThat(policy.calculateDelaySeconds(10)).isEqualTo(50);
    }
}
