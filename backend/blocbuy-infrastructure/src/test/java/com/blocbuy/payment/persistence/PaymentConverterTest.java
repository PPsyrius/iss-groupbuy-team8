package com.blocbuy.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.CheckoutQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Explicit row/aggregate conversion in both directions with lifecycle codes. */
class PaymentConverterTest {
    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    private BizPaymentDO row() {
        var row = new BizPaymentDO();
        row.setId(UUID.randomUUID());
        row.setIntentId(UUID.randomUUID());
        row.setUserId(UUID.randomUUID());
        row.setOperation("START");
        row.setBusinessId(UUID.randomUUID());
        row.setAmount(new BigDecimal("10.00"));
        row.setCurrency("sgd");
        row.setQuoteValidUntil(NOW.plusSeconds(3600));
        row.setBusinessDeadline(NOW.plusSeconds(7200));
        row.setRequestKey("key-1");
        row.setCreateTime(NOW);
        row.setSessionExpiresAt(NOW.plusSeconds(1800));
        row.setSuccessUrl("https://shop.example/success");
        row.setCancelUrl("https://shop.example/cancel");
        row.setCheckoutSessionId("cs_test_fixture");
        row.setCheckoutUrl("https://checkout.stripe.com/c/pay/test");
        row.setProviderPaymentIntentId("pi_fixture");
        row.setStatus("created");
        row.setVersion(0);
        return row;
    }

    private CheckoutPayment payment() {
        var quote =
                new CheckoutQuote(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "START",
                        UUID.randomUUID(),
                        new BigDecimal("10.00"),
                        "sgd",
                        NOW.plusSeconds(3600),
                        NOW.plusSeconds(7200));
        return new CheckoutPayment(
                UUID.randomUUID(),
                quote,
                "key-1",
                NOW,
                NOW.plusSeconds(1800),
                "https://shop.example/success",
                "https://shop.example/cancel",
                "cs_test_fixture",
                "https://checkout.stripe.com/c/pay/test",
                "pi_fixture",
                CheckoutPayment.Status.SUCCEEDED,
                0);
    }

    @Test
    void nullRowMapsToNullPayment() {
        assertThat(PaymentConverter.fromRow(null)).isNull();
    }

    @Test
    void rowMapsToPaymentWithUppercaseLifecycleStatus() {
        BizPaymentDO row = row();
        CheckoutPayment payment = PaymentConverter.fromRow(row);
        assertThat(payment.id()).isEqualTo(row.getId());
        assertThat(payment.quote().intentId()).isEqualTo(row.getIntentId());
        assertThat(payment.quote().userId()).isEqualTo(row.getUserId());
        assertThat(payment.quote().operation()).isEqualTo("START");
        assertThat(payment.quote().businessId()).isEqualTo(row.getBusinessId());
        assertThat(payment.quote().amount()).isEqualByComparingTo("10.00");
        assertThat(payment.quote().currency()).isEqualTo("sgd");
        assertThat(payment.quote().quoteValidUntil()).isEqualTo(row.getQuoteValidUntil());
        assertThat(payment.quote().businessDeadline()).isEqualTo(row.getBusinessDeadline());
        assertThat(payment.requestKey()).isEqualTo("key-1");
        assertThat(payment.createdAt()).isEqualTo(NOW);
        assertThat(payment.sessionExpiresAt()).isEqualTo(row.getSessionExpiresAt());
        assertThat(payment.successUrl()).isEqualTo("https://shop.example/success");
        assertThat(payment.cancelUrl()).isEqualTo("https://shop.example/cancel");
        assertThat(payment.sessionId()).isEqualTo("cs_test_fixture");
        assertThat(payment.checkoutUrl()).isEqualTo("https://checkout.stripe.com/c/pay/test");
        assertThat(payment.providerPaymentIntentId()).isEqualTo("pi_fixture");
        assertThat(payment.status()).isEqualTo(CheckoutPayment.Status.CREATED);
        assertThat(payment.version()).isZero();
    }

    @Test
    void paymentMapsToRowWithLowercaseLifecycleStatus() {
        CheckoutPayment payment = payment();
        BizPaymentDO row = PaymentConverter.toRow(payment);
        assertThat(row.getId()).isEqualTo(payment.id());
        assertThat(row.getIntentId()).isEqualTo(payment.quote().intentId());
        assertThat(row.getUserId()).isEqualTo(payment.quote().userId());
        assertThat(row.getOperation()).isEqualTo("START");
        assertThat(row.getBusinessId()).isEqualTo(payment.quote().businessId());
        assertThat(row.getAmount()).isEqualByComparingTo("10.00");
        assertThat(row.getCurrency()).isEqualTo("sgd");
        assertThat(row.getQuoteValidUntil()).isEqualTo(payment.quote().quoteValidUntil());
        assertThat(row.getBusinessDeadline()).isEqualTo(payment.quote().businessDeadline());
        assertThat(row.getRequestKey()).isEqualTo("key-1");
        assertThat(row.getCreateTime()).isEqualTo(NOW);
        assertThat(row.getSessionExpiresAt()).isEqualTo(payment.sessionExpiresAt());
        assertThat(row.getSuccessUrl()).isEqualTo("https://shop.example/success");
        assertThat(row.getCancelUrl()).isEqualTo("https://shop.example/cancel");
        assertThat(row.getCheckoutSessionId()).isEqualTo("cs_test_fixture");
        assertThat(row.getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/c/pay/test");
        assertThat(row.getProviderPaymentIntentId()).isEqualTo("pi_fixture");
        assertThat(row.getStatus()).isEqualTo("succeeded");
        assertThat(row.getVersion()).isZero();
    }
}
