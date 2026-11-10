package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/** Defaults hold with no configuration at all, and nonsense values stop startup. */
class SchedulingPropertiesTest {

    @Test
    void defaultsAreOffWithAOneMinuteSweepOfAHundredTeams() {
        SchedulingProperties properties = bind(Map.of());

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.purchaseIntentExpiry()).isEqualTo(properties.teamExpiry());
        assertThat(properties.teamExpiry())
                .isEqualTo(
                        new SchedulingProperties.ExpirySweep(
                                Duration.ofMinutes(1),
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(2),
                                100));
        assertThat(properties.refundWorker())
                .isEqualTo(
                        new SchedulingProperties.RefundWorker(
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(45),
                                Duration.ofMinutes(2)));
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
        Map<String, String> values = Map.of("blocbuy.scheduling.team-expiry.batch-size", "501");

        assertThatThrownBy(() -> bind(values))
                .isInstanceOf(BindException.class)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class);
        Map<String, String> invalidPurchaseIntentValues =
                Map.of("blocbuy.scheduling.purchase-intent-expiry.batch-size", "0");
        assertThatThrownBy(() -> bind(invalidPurchaseIntentValues))
                .isInstanceOf(BindException.class);
        assertThat(
                        bind(Map.of("blocbuy.scheduling.purchase-intent-expiry.batch-size", "7"))
                                .purchaseIntentExpiry()
                                .batchSize())
                .isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"application-local.yml", "application-ec2.yml", "application-docker.yml"})
    void deploymentProfilesRequireExplicitOptIn(String profile) throws java.io.IOException {
        var environment = new MockEnvironment();
        environment
                .getPropertySources()
                .addLast(
                        new YamlPropertySourceLoader()
                                .load(profile, new ClassPathResource(profile))
                                .getFirst());

        assertThat(environment.getProperty("blocbuy.scheduling.enabled", Boolean.class)).isFalse();

        environment.setProperty("BLOCBUY_SCHEDULING_ENABLED", "true");
        assertThat(environment.getProperty("blocbuy.scheduling.enabled", Boolean.class)).isTrue();
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
        assertThat(new SchedulingProperties.ExpirySweep(ok, Duration.ZERO, ok, 1).batchSize())
                .isEqualTo(1);

        assertRefundInvalid(null, ok, ok);
        assertRefundInvalid(Duration.ZERO, ok, ok);
        assertRefundInvalid(ok, null, ok);
        assertRefundInvalid(ok, Duration.ofSeconds(-1), ok);
        assertRefundInvalid(ok, ok, null);
        assertRefundInvalid(ok, ok, Duration.ofSeconds(1));
        assertThat(new SchedulingProperties.RefundWorker(ok, Duration.ZERO, ok).lease())
                .isEqualTo(ok);
    }

    private static void assertInvalid(
            Duration interval, Duration initialDelay, Duration lease, int batchSize) {
        assertThatThrownBy(() ->
                        new SchedulingProperties.ExpirySweep(
                                interval, initialDelay, lease, batchSize))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertRefundInvalid(
            Duration interval, Duration initialDelay, Duration lease) {
        assertThatThrownBy(
                        () -> new SchedulingProperties.RefundWorker(interval, initialDelay, lease))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SchedulingProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("blocbuy.scheduling", SchedulingProperties.class);
    }
}
