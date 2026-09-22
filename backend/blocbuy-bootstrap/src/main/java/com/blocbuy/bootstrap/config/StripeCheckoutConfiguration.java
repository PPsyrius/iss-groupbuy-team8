package com.blocbuy.bootstrap.config;

import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.service.StripeCheckoutService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Provider wiring for checkout, still explicit opt-in. The business collaborator it needs now
 * exists unconditionally ({@link PaymentIntegrationConfiguration}), so the profile is no longer a
 * guard against a missing implementation — it is the rollout switch, the same profile {@code
 * StripeFeatureSwitch} reads, and it also gates the beans whose {@code success-url} / {@code
 * cancel-url} properties are only supplied where checkout is actually configured.
 */
@Configuration
@Profile("stripe-sprint2")
public class StripeCheckoutConfiguration {
    @Bean
    com.blocbuy.payment.service.RefundService refundService(
            com.blocbuy.payment.repository.RefundRepository repository,
            com.blocbuy.payment.gateway.RefundGateway gateway,
            PlatformTransactionManager transactions) {
        return new com.blocbuy.payment.service.RefundService(repository, gateway, transactions);
    }

    /**
     * The refund half of team expiry. Without the checkout profile no buyer ever paid through this
     * application, so an expired team owes nobody money and only its orders need cancelling.
     */
    @Bean
    com.blocbuy.payment.service.TeamExpiryRefundSubscriber teamExpiryRefundSubscriber(
            StripeCheckoutService payments) {
        return new com.blocbuy.payment.service.TeamExpiryRefundSubscriber(payments);
    }

    @Bean
    StripeCheckoutService stripeCheckoutService(
            CheckoutPaymentRepository repository,
            CheckoutGateway gateway,
            PaymentBusinessPort business,
            PlatformTransactionManager transactions,
            @Value("${blocbuy.payment.checkout.success-url}") String successUrl,
            @Value("${blocbuy.payment.checkout.cancel-url}") String cancelUrl) {
        return new StripeCheckoutService(
                repository, gateway, business, transactions, successUrl, cancelUrl);
    }
}
