package com.blocbuy.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Provider-neutral payment gateway value contracts. */
class PaymentGatewayTest {

    @Test
    void createCommandExposesProviderFreePaymentFacts() {
        PaymentGateway.CreatePaymentIntentCommand command =
                new PaymentGateway.CreatePaymentIntentCommand(
                        "PAY-1", "ORDER-1", new BigDecimal("12.34"), "sgd", "key-1");

        assertThat(command.paymentNo()).isEqualTo("PAY-1");
        assertThat(command.orderId()).isEqualTo("ORDER-1");
        assertThat(command.amount()).isEqualByComparingTo("12.34");
        assertThat(command.currency()).isEqualTo("sgd");
        assertThat(command.idempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void providerReferenceExposesConfirmationValues() {
        PaymentGateway.ProviderPaymentRef reference =
                new PaymentGateway.ProviderPaymentRef("pi_123", "secret_123");

        assertThat(reference.providerPaymentIntentId()).isEqualTo("pi_123");
        assertThat(reference.clientSecret()).isEqualTo("secret_123");
    }

    @Test
    void providerEventExposesVerifiedEventFacts() {
        PaymentGateway.ProviderEvent event =
                new PaymentGateway.ProviderEvent("evt_123", "payment_intent.succeeded", "pi_123");

        assertThat(event.eventId()).isEqualTo("evt_123");
        assertThat(event.eventType()).isEqualTo("payment_intent.succeeded");
        assertThat(event.providerPaymentIntentId()).isEqualTo("pi_123");
    }
}
