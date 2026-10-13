package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.blocbuy.bootstrap.scheduling.LeasedJobRunner;
import com.blocbuy.bootstrap.scheduling.OutboxRelayJob;
import com.blocbuy.bootstrap.scheduling.OutboxRetentionJob;
import com.blocbuy.bootstrap.scheduling.PurchaseIntentExpiryJob;
import com.blocbuy.bootstrap.scheduling.RefundWorkerJob;
import com.blocbuy.bootstrap.scheduling.TeamExpirySweepJob;
import com.blocbuy.common.service.OutboxRelayService;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.service.UserAppServiceImpl;
import com.blocbuy.identity.web.internal.InternalUserController;
import com.blocbuy.integration.outbox.IntegrationOutboxWriter;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Context smoke test. Flyway is disabled and datasource/Redis point at placeholders — this test
 * proves the wiring assembles, not that infrastructure is reachable. Real connectivity is covered
 * by the repository integration tests (Testcontainers) in the infrastructure module.
 */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/blocbuy_placeholder?currentSchema=domain",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1"
        })
class BlocBuyApplicationTest {

    @Autowired private ApplicationContext context;

    @Test
    void contextLoads() {
        // assembly success is the assertion
    }

    /** Scheduling is opt-in per deploy profile; a context that does not ask gets no jobs. */
    @Test
    void noJobIsScheduledUnlessTheSwitchIsOn() {
        assertThat(context.getBeansOfType(TeamExpirySweepJob.class)).isEmpty();
        assertThat(context.getBeansOfType(PurchaseIntentExpiryJob.class)).isEmpty();
        assertThat(context.getBeansOfType(RefundWorkerJob.class)).isEmpty();
        assertThat(context.getBeansOfType(LeasedJobRunner.class)).isEmpty();
        assertThat(context.getBeansOfType(IntegrationOutboxWriter.class)).isEmpty();
        assertThat(context.getBeansOfType(OutboxRetentionJob.class)).isEmpty();
        assertThat(context.getBeansOfType(OutboxRelayService.class)).isEmpty();
        assertThat(context.getBeansOfType(OutboxRelayJob.class)).isEmpty();
    }

    /** The monolith answers identity in process and never serves /internal/** (ADR 0008, #468). */
    @Test
    void identityIsLocalAndInternalEndpointsAreAbsent() {
        assertThat(context.getBean(UserAppService.class)).isInstanceOf(UserAppServiceImpl.class);
        assertThat(context.getBeansOfType(InternalUserController.class)).isEmpty();
    }

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {"--spring.profiles.active=test"};

        try (MockedStatic<SpringApplication> springApplication =
                mockStatic(SpringApplication.class)) {
            BlocBuyApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(BlocBuyApplication.class, args));
        }
    }
}
