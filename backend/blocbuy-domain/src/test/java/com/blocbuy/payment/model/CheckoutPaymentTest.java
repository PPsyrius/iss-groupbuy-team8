package com.blocbuy.payment.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Checkout snapshot is separate from dev's unchanged identity-only Payment model. */
class CheckoutPaymentTest {
    @Test
    void retainsUuidIdentityAndImmutableServerQuote() {
        CheckoutPayment payment = payment();
        assertThat(payment.id()).isNotNull();
        assertThat(payment.quote().amount()).isEqualByComparingTo("10.00");
        assertThat(payment.status()).isEqualTo(CheckoutPayment.Status.CREATED);
    }

    @Test
    void cannotRebindProviderSession() {
        CheckoutPayment bound = payment().bind("cs_test_first", null, "pi_first");
        assertThatThrownBy(() -> bound.bind("cs_test_other", null, "pi_other"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellationCannotDowngradePaidMoney() {
        assertThat(payment().succeed().cancelUnpaid().status())
                .isEqualTo(CheckoutPayment.Status.SUCCEEDED);
    }

    @Test
    void paidFactsCanRepairCanceledAttempt() {
        assertThat(payment().cancelUnpaid().succeed().status())
                .isEqualTo(CheckoutPayment.Status.SUCCEEDED);
    }

    @Test
    void requestKeyAndQuoteDeadlineAreValidated() {
        CheckoutPayment p = payment();
        assertThatThrownBy(
                        () ->
                                new CheckoutPayment(
                                        p.id(),
                                        p.quote(),
                                        " ",
                                        p.createdAt(),
                                        p.sessionExpiresAt(),
                                        p.successUrl(),
                                        p.cancelUrl(),
                                        null,
                                        null,
                                        null,
                                        CheckoutPayment.Status.CREATED,
                                        0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidSnapshotVersionExpiryAndMissingRequestKeyAreRejected() {
        CheckoutPayment p = payment();
        assertThatThrownBy(() -> snapshot(p, null, p.sessionExpiresAt(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> snapshot(p, "key", p.sessionExpiresAt(), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> snapshot(p, "key", p.quote().quoteValidUntil().plusSeconds(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidOrConflictingProviderReferencesAreRejected() {
        CheckoutPayment bound =
                payment().bind("cs_first", "https://example.com/checkout", "pi_first");
        assertThatThrownBy(() -> bound.bind(null, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> bound.bind(" ", null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> bound.bind("cs_first", null, "pi_other"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(bound.bind("cs_first", null, null)).isEqualTo(bound);
    }

    @Test
    void terminalUnpaidFailureCannotDowngradeSuccessfulPayment() {
        CheckoutPayment paid = payment().succeed();
        assertThat(paid.failTerminalUnpaid()).isSameAs(paid);
        assertThat(payment().failTerminalUnpaid().status())
                .isEqualTo(CheckoutPayment.Status.FAILED);
    }

    private CheckoutPayment snapshot(CheckoutPayment p, String key, Instant expiry, int version) {
        return new CheckoutPayment(
                p.id(),
                p.quote(),
                key,
                p.createdAt(),
                expiry,
                p.successUrl(),
                p.cancelUrl(),
                null,
                null,
                null,
                p.status(),
                version);
    }

    private CheckoutPayment payment() {
        Instant now = Instant.now();
        var quote =
                new CheckoutQuote(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "START",
                        UUID.randomUUID(),
                        new BigDecimal("10.00"),
                        "sgd",
                        now.plusSeconds(3600),
                        now.plusSeconds(7200));
        return new CheckoutPayment(
                UUID.randomUUID(),
                quote,
                "test-key",
                now,
                now.plusSeconds(1860),
                "http://localhost/success",
                "http://localhost/cancel",
                null,
                null,
                null,
                CheckoutPayment.Status.CREATED,
                0);
    }
}
