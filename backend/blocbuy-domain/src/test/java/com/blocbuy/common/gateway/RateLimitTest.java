package com.blocbuy.common.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.gateway.RateLimitGateway.RateLimit;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** A rate limit is valid from birth: a safe key segment, a positive count, whole seconds. */
class RateLimitTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    @Test
    void aValidLimitKeepsItsValues() {
        RateLimit limit = new RateLimit("invite-resolve", 60, Duration.ofMinutes(1));

        assertThat(limit.scope()).isEqualTo("invite-resolve");
        assertThat(limit.maxRequests()).isEqualTo(60);
        assertThat(limit.window()).isEqualTo(Duration.ofSeconds(60));
        assertThat(new RateLimit("a1", 1, Duration.ofSeconds(1)).maxRequests()).isEqualTo(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {"Invite", "invite_resolve", "invite:resolve", "-invite", "invite-", "a--b"})
    void aScopeThatIsNotLowercaseKebabCaseIsRejected(String scope) {
        assertThatThrownBy(() -> new RateLimit(scope, 60, MINUTE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fewerThanOneRequestIsRejected() {
        assertThatThrownBy(() -> new RateLimit("invite-resolve", 0, MINUTE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingWindowIsRejected() {
        assertThatThrownBy(() -> new RateLimit("invite-resolve", 60, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {999, 1500, -1000, 0})
    void aSubSecondFractionalOrNonPositiveWindowIsRejected(long millis) {
        Duration window = Duration.ofMillis(millis);

        assertThatThrownBy(() -> new RateLimit("invite-resolve", 60, window))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLongScopeIsMatchedWithoutBacktracking() {
        String longScope = "a-".repeat(50_000) + "a";

        assertThat(new RateLimit(longScope, 1, MINUTE).scope()).isEqualTo(longScope);
    }
}
