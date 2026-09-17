package com.blocbuy.notification.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Notification identity invariants. */
class NotificationTest {

    @Test
    void ofCreatesNotificationWithIdentity() {
        Notification notification = Notification.of("notification-1");

        assertThat(notification.id()).isEqualTo("notification-1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void ofRejectsBlankIdentity(String id) {
        assertThatThrownBy(() -> Notification.of(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must not be blank");
    }
}
