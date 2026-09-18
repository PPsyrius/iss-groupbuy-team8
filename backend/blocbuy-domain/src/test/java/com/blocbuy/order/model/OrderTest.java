package com.blocbuy.order.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Order identity invariants. */
class OrderTest {

    @Test
    void ofCreatesOrderWithIdentity() {
        Order order = Order.of("order-1");

        assertThat(order.id()).isEqualTo("order-1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void ofRejectsBlankIdentity(String id) {
        assertThatThrownBy(() -> Order.of(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must not be blank");
    }
}
