package com.blocbuy.notification.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.notification.exception.NotificationErrorCode;
import org.junit.jupiter.api.Test;

class NotificationErrorCodeTest {
    @Test
    void codesKeepTheirPublishedStatusAndLocalizationSeams() {
        var existing = NotificationErrorCode.NOTIFICATION_PLACEHOLDER;
        assertThat(existing.code()).isEqualTo("NOTIFICATION_404_001");
        assertThat(existing.httpStatus()).isEqualTo(404);
        assertThat(existing.defaultMessage()).isEqualTo("Notification not found");
        assertThat(existing.messageKey()).isEqualTo("error.notification.notFound");
        var invalid = NotificationErrorCode.NOTIFICATION_INVALID;
        assertThat(invalid.code()).isEqualTo("NOTIFICATION_400_001");
        assertThat(invalid.httpStatus()).isEqualTo(400);
        assertThat(invalid.defaultMessage()).isEqualTo("Invalid notification");
        assertThat(invalid.messageKey()).isEqualTo("error.notification.invalid");
    }
}
