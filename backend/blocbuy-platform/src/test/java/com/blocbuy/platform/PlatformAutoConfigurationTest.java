package com.blocbuy.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** A service assembly runs distributed or not at all (backend ADR 0008). */
class PlatformAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(PlatformAutoConfiguration.class));

    @Test
    void startsWhenDistributed() {
        runner.withPropertyValues("blocbuy.topology=distributed")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(TopologyProperties.class).topology())
                                    .isEqualTo(TopologyProperties.Mode.DISTRIBUTED);
                            assertThat(context).hasSingleBean(RestClientCustomizer.class);
                        });
    }

    @Test
    void refusesTheDefaultMonolithTopology() {
        runner.run(
                context ->
                        assertThat(context)
                                .getFailure()
                                .rootCause()
                                .hasMessageContaining(
                                        "must run with blocbuy.topology=distributed, not monolith"));
    }

    @Test
    void refusesAMisspelledTopology() {
        runner.withPropertyValues("blocbuy.topology=distrbuted")
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .hasStackTraceContaining("blocbuy.topology"));
    }
}
