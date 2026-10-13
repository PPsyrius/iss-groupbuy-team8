package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.model.OutboxEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OutboxMessageFactoryTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void serializesVersionedEnvelopeAndRequiredAttributes() {
        var event = event("{\"ok\":true}");

        assertThat(OutboxMessageFactory.body(event, json))
                .contains("\"eventId\"", "\"eventType\":\"TeamExpired\"", "\"ok\":true");
        assertThat(OutboxMessageFactory.attributes(event))
                .containsEntry("eventType", "TeamExpired")
                .containsEntry("version", "1")
                .containsEntry("traceId", "trace-test");
    }

    @Test
    void rejectsAnInvalidStoredPayload() {
        OutboxEvent invalid = event("not-json");
        assertThatThrownBy(() -> OutboxMessageFactory.body(invalid, json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not serialize outbox event");
    }

    private static OutboxEvent event(String payload) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "TeamExpired",
                1,
                UUID.randomUUID(),
                payload,
                "trace-test",
                Instant.parse("2026-10-12T00:00:00Z"),
                0);
    }
}
