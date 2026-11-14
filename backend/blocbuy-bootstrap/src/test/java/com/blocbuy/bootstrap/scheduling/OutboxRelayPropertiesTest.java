package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

class OutboxRelayPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultDelayAndTimerWiringPreserveNormalBehavior() throws Exception {
        runner.run(context ->
                assertThat(context.getBean(OutboxRelayProperties.class).interval())
                        .isEqualTo(Duration.ofSeconds(2)));
        var scheduled = OutboxRelayJob.class.getMethod("run").getAnnotation(Scheduled.class);
        assertThat(scheduled.fixedDelayString()).isEqualTo("${blocbuy.events.relay.interval:PT2S}");
        assertThat(scheduled.initialDelay()).isEqualTo(2_000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0.1S", "PT0.25S", "PT2S", "PT1M"})
    void acceptsBoundedIntervalsAtStartup(String interval) {
        runner.withPropertyValues("blocbuy.events.relay.interval=" + interval)
                .run(context ->
                        assertThat(context.getBean(OutboxRelayProperties.class).interval())
                                .isEqualTo(Duration.parse(interval)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1S", "PT0.099S", "PT60.001S"})
    void rejectsOutOfBoundsIntervalsAtStartup(String interval) {
        runner.withPropertyValues("blocbuy.events.relay.interval=" + interval)
                .run(context ->
                        assertThat(context)
                                .getFailure()
                                .hasRootCauseMessage(
                                        "blocbuy.events.relay.interval must be between PT0.1S and PT1M"));
    }

    @Test
    void rejectsNullInterval() {
        assertThatThrownBy(() -> new OutboxRelayProperties(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OutboxRelayProperties.class)
    static class PropertiesConfiguration {}
}
