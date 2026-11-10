package com.blocbuy.notification.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {
    private static final UUID ID = UUID.fromString("01990000-0000-7000-8000-000000000001");
    private static final Instant TIME = Instant.parse("2026-10-13T00:00:00Z");

    @Test
    void acceptsAllAgreedEventTypesAndTextBoundaries() {
        for (var type : NotificationType.values()) {
            var notification =
                    new Notification(ID, ID, ID, type, ID, "x".repeat(128), "x".repeat(1024), TIME);
            assertThat(notification.id()).isEqualTo(ID);
            assertThat(notification.type()).isEqualTo(type);
            assertThat(NotificationType.fromCode(type.code())).isEqualTo(type);
        }
    }

    @Test
    void unknownEventTypesAreRejected() {
        assertThatThrownBy(() -> NotificationType.fromCode("Unknown"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> NotificationType.fromCode(null)).isInstanceOf(BizException.class);
    }

    @Test
    void rejectsEveryMissingIdentityTypeOrTimestamp() {
        assertInvalid(null, ID, ID, NotificationType.TEAM_SUCCEEDED, ID, "title", "body", TIME);
        assertInvalid(ID, null, ID, NotificationType.TEAM_SUCCEEDED, ID, "title", "body", TIME);
        assertInvalid(ID, ID, null, NotificationType.TEAM_SUCCEEDED, ID, "title", "body", TIME);
        assertInvalid(ID, ID, ID, null, ID, "title", "body", TIME);
        assertInvalid(ID, ID, ID, NotificationType.TEAM_SUCCEEDED, null, "title", "body", TIME);
        assertInvalid(ID, ID, ID, NotificationType.TEAM_SUCCEEDED, ID, "title", "body", null);
    }

    @Test
    void rejectsMissingBlankAndOversizedText() {
        for (String invalid : new String[] {null, "", " ", "x".repeat(129)}) {
            assertInvalid(ID, ID, ID, NotificationType.TEAM_SUCCEEDED, ID, invalid, "body", TIME);
        }
        for (String invalid : new String[] {null, "", "\t", "x".repeat(1025)}) {
            assertInvalid(ID, ID, ID, NotificationType.TEAM_SUCCEEDED, ID, "title", invalid, TIME);
        }
    }

    private static void assertInvalid(
            UUID id,
            UUID event,
            UUID user,
            NotificationType type,
            UUID reference,
            String title,
            String content,
            Instant time) {
        assertThatThrownBy(() ->
                        new Notification(id, event, user, type, reference, title, content, time))
                .isInstanceOf(BizException.class)
                .hasMessage("NOTIFICATION_400_001: Invalid notification");
    }
}
