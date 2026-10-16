package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.bootstrap.scheduling.SchedulingProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/** Exercise the real YAML bridge with the exact environment names supplied by Terraform. */
class SchedulingEnvironmentTest {
    @Test
    void v4EnvironmentOverridesBothBoundSettingsAndScheduledPlaceholder() throws IOException {
        var environment =
                environment(
                        Map.of(
                                "BLOCBUY_SCHEDULING_TEAM_EXPIRY_BATCH_SIZE", "500",
                                "BLOCBUY_SCHEDULING_TEAM_EXPIRY_INTERVAL", "PT1S"));
        var settings =
                Binder.get(environment)
                        .bindOrCreate("blocbuy.scheduling", SchedulingProperties.class);
        assertThat(settings.teamExpiry().batchSize()).isEqualTo(500);
        assertThat(settings.teamExpiry().interval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(
                        environment.resolveRequiredPlaceholders(
                                "${blocbuy.scheduling.team-expiry.interval:PT1M}"))
                .isEqualTo("PT1S");
    }

    @Test
    void ordinarySessionsKeepDefaultSweepSettings() throws IOException {
        var settings =
                Binder.get(environment(Map.of()))
                        .bindOrCreate("blocbuy.scheduling", SchedulingProperties.class);
        assertThat(settings.teamExpiry().batchSize()).isEqualTo(100);
        assertThat(settings.teamExpiry().interval()).isEqualTo(Duration.ofMinutes(1));
    }

    private StandardEnvironment environment(Map<String, Object> variables) throws IOException {
        var environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment
                .getPropertySources()
                .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment
                .getPropertySources()
                .addFirst(new SystemEnvironmentPropertySource("terraform", variables));
        for (var source :
                new YamlPropertySourceLoader()
                        .load("application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }
}
