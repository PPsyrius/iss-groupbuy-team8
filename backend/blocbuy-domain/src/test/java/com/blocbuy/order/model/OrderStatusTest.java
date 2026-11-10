package com.blocbuy.order.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Order status code round-trips and the exhaustive transition matrix. */
class OrderStatusTest {

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void codesRoundTripThroughFromCode(OrderStatus status) {
        assertThat(OrderStatus.fromCode(status.code())).isEqualTo(status);
    }

    @Test
    void fromCodeFailsFastOnUnknownCode() {
        assertThatThrownBy(() -> OrderStatus.fromCode("shipped"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown order status code: shipped");
    }

    @ParameterizedTest
    @MethodSource("matrix")
    void onlyPendingMayMoveAndNeverToItself(OrderStatus from, OrderStatus to) {
        boolean expected = from == OrderStatus.PENDING && to != OrderStatus.PENDING;
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> matrix() {
        return java.util.Arrays.stream(OrderStatus.values())
                .flatMap(from ->
                        java.util.Arrays.stream(OrderStatus.values())
                                .map(to ->
                                        org.junit.jupiter.params.provider.Arguments.of(from, to)));
    }

    @Test
    void nullTargetIsNeverReachable() {
        assertThat(OrderStatus.PENDING.canTransitionTo(null)).isFalse();
    }
}
