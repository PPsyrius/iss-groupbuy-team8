package com.blocbuy.payment.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Payment identity invariants. */
class PaymentTest {

    @Test
    void ofCreatesPaymentWithIdentity() {
        Payment payment = Payment.of("payment-1");

        assertThat(payment.id()).isEqualTo("payment-1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void ofRejectsBlankIdentity(String id) {
        assertThatThrownBy(() -> Payment.of(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must not be blank");
    }
}
