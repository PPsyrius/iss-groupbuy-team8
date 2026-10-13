package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.bootstrap.scheduling.OutboxRetentionJob;
import com.blocbuy.integration.outbox.IntegrationOutboxWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * The writer can be enabled independently; retention still requires the scheduler master switch.
 */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/blocbuy_placeholder?currentSchema=domain",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "blocbuy.events.enabled=true",
            "blocbuy.scheduling.enabled=false"
        })
class EventsEnabledContextTest {

    @Autowired private ApplicationContext context;

    @Test
    void eventWriterIsOptedInWithoutAccidentallyStartingScheduledJobs() {
        assertThat(context.getBeansOfType(IntegrationOutboxWriter.class)).hasSize(1);
        assertThat(context.getBeansOfType(OutboxRetentionJob.class)).isEmpty();
    }
}
