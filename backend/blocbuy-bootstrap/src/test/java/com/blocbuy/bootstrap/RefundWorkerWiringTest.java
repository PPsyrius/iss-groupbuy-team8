package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.bootstrap.scheduling.RefundWorkerJob;
import com.blocbuy.bootstrap.scheduling.TeamExpirySweepJob;
import com.blocbuy.payment.service.RefundService;
import com.blocbuy.payment.service.StripeCheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * The deployment scheduling switch activates refunds without enabling checkout's Stripe profile.
 */
@SpringBootTest(
        properties = {
            "blocbuy.scheduling.enabled=true",
            "blocbuy.scheduling.refund-worker.initial-delay=PT1H",
            "blocbuy.scheduling.team-expiry.initial-delay=PT1H",
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/blocbuy_placeholder?currentSchema=domain",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "spring.data.redis.password=test",
            "blocbuy.frontend.success-redirect-uri=https://example.test/auth/callback",
            "blocbuy.frontend.failure-redirect-uri=https://example.test/login?error={errorCode}"
        })
@ActiveProfiles("ec2")
class RefundWorkerWiringTest {

    @Autowired private ApplicationContext context;

    @Test
    void schedulingWiresRefundsWithoutCheckoutProfile() {
        assertThat(context.getBeansOfType(TeamExpirySweepJob.class)).hasSize(1);
        assertThat(context.getBeansOfType(RefundWorkerJob.class)).hasSize(1);
        assertThat(context.getBeansOfType(RefundService.class)).hasSize(1);
        assertThat(context.getBeansOfType(StripeCheckoutService.class)).isEmpty();
        assertThat(context.getEnvironment().acceptsProfiles("stripe-sprint2")).isFalse();
    }
}
