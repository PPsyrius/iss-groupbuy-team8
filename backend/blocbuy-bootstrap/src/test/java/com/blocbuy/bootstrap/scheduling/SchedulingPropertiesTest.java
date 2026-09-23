package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** Defaults hold with no configuration at all, and nonsense values stop startup. */
class SchedulingPropertiesTest {

    @Test
    void defaultsAreOffWithAOneMinuteSweepOfAHundredTeams() {
        SchedulingProperties properties = bind(Map.of());

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.teamExpiry())
                .isEqualTo(
                        new SchedulingProperties.TeamExpiry(
                                Duration.ofMinutes(1),
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(2),
                                100));
    }

    @Test
    void relaxedPropertyNamesBind() {
        SchedulingProperties properties =
                bind(
                        Map.of(
                                "blocbuy.scheduling.enabled", "true",
                                "blocbuy.scheduling.team-expiry.interval", "PT5M",
                                "blocbuy.scheduling.team-expiry.initial-delay", "PT0S",
                                "blocbuy.scheduling.team-expiry.lease", "PT10M",
                                "blocbuy.scheduling.team-expiry.batch-size", "500"));

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.teamExpiry().interval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.teamExpiry().initialDelay()).isZero();
        assertThat(properties.teamExpiry().batchSize()).isEqualTo(500);
    }

    @Test
    void anOutOfRangeValueFailsBinding() {
        assertThatThrownBy(() -> bind(Map.of("blocbuy.scheduling.team-expiry.batch-size", "501")))
                .isInstanceOf(BindException.class)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everySettingIsGuarded() {
        Duration ok = Duration.ofMinutes(1);

        assertInvalid(null, ok, ok, 1);
        assertInvalid(Duration.ZERO, ok, ok, 1);
        assertInvalid(ok, null, ok, 1);
        assertInvalid(ok, Duration.ofSeconds(-1), ok, 1);
        assertInvalid(ok, ok, null, 1);
        assertInvalid(ok, ok, Duration.ofNanos(1), 1);
        assertInvalid(ok, ok, ok, 0);
        assertInvalid(ok, ok, ok, SchedulingProperties.MAX_BATCH_SIZE + 1);
        assertThat(new SchedulingProperties.TeamExpiry(ok, Duration.ZERO, ok, 1).batchSize())
                .isEqualTo(1);
    }

    private static void assertInvalid(
            Duration interval, Duration initialDelay, Duration lease, int batchSize) {
        assertThatThrownBy(
                        () ->
                                new SchedulingProperties.TeamExpiry(
                                        interval, initialDelay, lease, batchSize))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SchedulingProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("blocbuy.scheduling", SchedulingProperties.class);
    }
}
