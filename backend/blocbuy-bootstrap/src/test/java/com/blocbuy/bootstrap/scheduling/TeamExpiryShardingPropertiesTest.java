package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** Team-only strict opt-in binding, including typo rejection and unchanged other job settings. */
class TeamExpiryShardingPropertiesTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultsKeepEveryExistingJobSettingAndUseOneShard() {
        context.run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(c.getBean(TeamExpiryShardingProperties.class).count()).isEqualTo(1);
            var jobs = c.getBean(SchedulingProperties.class);
            assertThat(jobs.enabled()).isFalse();
            assertThat(jobs.teamExpiry()).isEqualTo(jobs.purchaseIntentExpiry());
            assertThat(jobs.refundWorker().interval()).hasSeconds(30);
        });
    }

    @Test
    void explicitSixShardsDoNotAlterUnpaidExpiryOrRefundSettings() {
        context.withPropertyValues("blocbuy.scheduling.team-expiry.sharding.count=6")
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    assertThat(c.getBean(TeamExpiryShardingProperties.class).count()).isEqualTo(6);
                    assertThat(
                                    c.getBean(SchedulingProperties.class)
                                            .purchaseIntentExpiry()
                                            .batchSize())
                            .isEqualTo(100);
                    assertThat(c.getBean(SchedulingProperties.class).refundWorker().interval())
                            .hasSeconds(30);
                });
    }

    @Test
    void nativeEnvironmentNameBindsWithoutAnAlias() {
        context.withInitializer(c ->
                        c.getEnvironment()
                                .getPropertySources()
                                .addFirst(
                                        new SystemEnvironmentPropertySource(
                                                "systemEnvironment",
                                                Map.of(
                                                        "BLOCBUY_SCHEDULING_TEAMEXPIRY_SHARDING_COUNT",
                                                        "6"))))
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    assertThat(c.getBean(TeamExpiryShardingProperties.class).count()).isEqualTo(6);
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 7, Integer.MAX_VALUE})
    void invalidCountsFailBeforeAnyJobStarts(int count) {
        assertThatThrownBy(() -> new TeamExpiryShardingProperties(count))
                .isInstanceOf(IllegalArgumentException.class);
        context.withPropertyValues("blocbuy.scheduling.team-expiry.sharding.count=" + count)
                .run(c -> assertThat(c).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown=6", "count=oops"})
    void unknownAndMalformedSettingsFailClosed(String setting) {
        context.withPropertyValues("blocbuy.scheduling.team-expiry.sharding." + setting)
                .run(c -> assertThat(c).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({SchedulingProperties.class, TeamExpiryShardingProperties.class})
    static class PropertiesConfiguration {}
}
