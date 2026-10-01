package com.blocbuy.bootstrap.config;

import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.service.StripeCheckoutService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Provider wiring for Checkout, explicitly opted in per deployment. Credentials and return URLs are
 * validated only in this branch; disabled deployments need no Stripe configuration.
 */
@Configuration
@ConditionalOnProperty(
        prefix = "blocbuy.payment.stripe",
        name = "checkout-enabled",
        havingValue = "true")
@EnableConfigurationProperties({
    StripeCheckoutRuntimeProperties.class,
    CheckoutRedirectProperties.class
})
public class StripeCheckoutConfiguration {
    @Bean
    StripeCheckoutService stripeCheckoutService(
            CheckoutPaymentRepository repository,
            CheckoutGateway gateway,
            PaymentBusinessPort business,
            PlatformTransactionManager transactions,
            CheckoutRedirectProperties redirects) {
        return new StripeCheckoutService(
                repository,
                gateway,
                business,
                transactions,
                redirects.successUrl(),
                redirects.cancelUrl());
    }
}
