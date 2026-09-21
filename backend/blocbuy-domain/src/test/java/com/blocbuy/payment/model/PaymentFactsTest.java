package com.blocbuy.payment.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Provider session facts: paid and terminal-unpaid failure semantics. */
class PaymentFactsTest {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @ParameterizedTest
    @CsvSource({
        "open,paid,pi_x,canceled,false,false",
        "complete,unpaid,pi_x,canceled,false,false",
        "complete,paid,pi_x,succeeded,true,false",
        "expired,paid,pi_x,canceled,false,false",
        "expired,unpaid,,canceled,false,false",
        "expired,unpaid,pi_x,processing,false,false",
        "expired,unpaid,pi_x,canceled,false,true"
    })
    void providerFactsDoNotConfusePaymentWithTerminalFailure(
            String state,
            String payment,
            String intent,
            String intentState,
            boolean paid,
            boolean failed) {
        var session =
                new CheckoutGateway.Session(
                        "cs_test",
                        "p",
                        "i",
                        intent,
                        100,
                        "sgd",
                        state,
                        payment,
                        false,
                        null,
                        Instant.EPOCH,
                        intentState);
        assertThat(session.paid()).isEqualTo(paid);
        assertThat(session.terminalUnpaidFailure()).isEqualTo(failed);
    }

    @Test
    void businessAcceptanceRequiresBothMembershipAndOrder() {
        assertThatThrownBy(() -> new PaymentBusinessPort.Outcome(null, ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentBusinessPort.Outcome(ID, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentBusinessPort.Outcome(null, null, "sensitive text"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PaymentBusinessPort.Outcome(ID, ID, null).accepted()).isTrue();
        assertThat(new PaymentBusinessPort.Outcome(null, null, "FULL").accepted()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "created,10,10,sgd,sgd,pi_x,false",
        "succeeded,0,0,sgd,sgd,pi_x,false",
        "succeeded,10,9,sgd,sgd,pi_x,false",
        "succeeded,10,10,sgd,usd,pi_x,false",
        "succeeded,10,10,usd,usd,pi_x,false",
        "succeeded,10,10,sgd,sgd,,false",
        "succeeded,10,10,sgd,sgd,pi_x,true"
    })
    void refundRequiresMatchingPaidMoney(
            String state,
            BigDecimal amount,
            BigDecimal paid,
            String currency,
            String paidCurrency,
            String intent,
            boolean valid) {
        var work =
                new RefundWork(
                        ID,
                        ID,
                        amount,
                        currency,
                        intent,
                        state,
                        paid,
                        paidCurrency,
                        null,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        1);
        assertThat(work.valid()).isEqualTo(valid);
        assertThat(work.providerKey()).isEqualTo("blocbuy-refund-" + ID);
    }
}
