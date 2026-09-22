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

/** Explicit opt-in; startup fails if the team has not supplied the real business collaborator. */
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
