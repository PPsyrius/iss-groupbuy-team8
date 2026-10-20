package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.blocbuy.bootstrap.scheduling.LeasedJobRunner;
import com.blocbuy.bootstrap.scheduling.OutboxRelayJob;
import com.blocbuy.common.repository.SchedulerLeaseRepository;
import com.blocbuy.common.service.OutboxRelayService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.sqs.SqsClient;

/** The relay is independently opt-in and still uses the common scheduler lease. */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/blocbuy_placeholder?currentSchema=domain",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "blocbuy.scheduling.enabled=false",
            "blocbuy.events.enabled=true",
            "blocbuy.events.relay.enabled=true",
            "blocbuy.events.transport=sqs",
            "blocbuy.events.region=us-east-1",
            "blocbuy.events.sqs.endpoint=http://localhost:9324",
            "blocbuy.events.sqs.queue-url=http://localhost:9324/queue"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxRelayEnabledContextTest {

    @Autowired private ApplicationContext context;

    // This is a wiring test: no timer tick may connect to the placeholder database.
    @MockitoBean(enforceOverride = true)
    private SchedulerLeaseRepository leases;

    @Test
    void registersRelayAndLeaseWithoutEnablingOtherScheduledJobs() {
        assertThat(context.getBeansOfType(OutboxRelayService.class)).hasSize(1);
        assertThat(context.getBeansOfType(OutboxRelayJob.class)).hasSize(1);
        assertThat(context.getBeansOfType(LeasedJobRunner.class)).hasSize(1);
        assertThat(context.getBeansOfType(SqsClient.class)).hasSize(1);
    }

    @Test
    void relayTicksUseTheIsolatedLeasePort() {
        context.getBean(OutboxRelayJob.class).run();
        verify(leases, atLeastOnce())
                .tryAcquire(eq("outbox-relay"), anyString(), eq(Duration.ofMinutes(2)));
    }
}
