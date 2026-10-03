package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.bootstrap.scheduling.RefundWorkerJob;
import com.blocbuy.bootstrap.scheduling.TeamExpirySweepJob;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.gateway.PaymentIntentPort;
import com.blocbuy.payment.service.StripeCheckoutService;
import com.blocbuy.payment.service.StripeFeatureSwitch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** Rehearses dev's Checkout flag without modifying the shared compose file or connecting to dev. */
@SpringBootTest(
        properties = {
            "blocbuy.payment.stripe.checkout-enabled=true",
            "blocbuy.payment.stripe.api-key=sk_test_local_fixture",
            "blocbuy.payment.stripe.webhook-secret=whsec_local_fixture",
            "blocbuy.scheduling.enabled=true",
            "blocbuy.scheduling.team-expiry.initial-delay=PT1H",
            "blocbuy.scheduling.refund-worker.initial-delay=PT1H",
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
            "spring.datasource.username=unused",
            "spring.datasource.password=unused",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "blocbuy.frontend.success-redirect-uri=https://example.test/auth/callback",
            "blocbuy.frontend.failure-redirect-uri=https://example.test/login?error={errorCode}",
            "blocbuy.payment.checkout.success-url=https://example.test/payments/return",
            "blocbuy.payment.checkout.cancel-url=https://example.test/payments/return"
        })
@ActiveProfiles("ec2")
@DirtiesContext
class StripeEc2RolloutWiringTest {
    @Autowired private ApplicationContext context;
    @Autowired private StripeFeatureSwitch checkout;

    @Test
    void checkoutFlagEnablesCheckoutAndBothJobsAlongsideTheExistingEc2Profile() {
        assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("ec2");
        assertThat(checkout.enabled()).isTrue();
        assertThat(context.getBeansOfType(StripeCheckoutService.class)).hasSize(1);
        assertThat(context.getBeansOfType(TeamExpirySweepJob.class)).hasSize(1);
        assertThat(context.getBeansOfType(RefundWorkerJob.class)).hasSize(1);
        assertThat(context.getBeansOfType(PaymentIntentPort.class)).hasSize(1);
        assertThat(context.getBeansOfType(PaymentBusinessPort.class)).hasSize(1);
    }
}
