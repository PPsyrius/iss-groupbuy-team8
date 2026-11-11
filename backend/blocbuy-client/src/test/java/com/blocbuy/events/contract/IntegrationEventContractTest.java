package com.blocbuy.events.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class IntegrationEventContractTest {

    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    @Test
    void teamSucceededV1JsonShapeIsStable() throws Exception {
        var event = new TeamSucceededV1(id("1"), id("2"), 3, List.of(id("3"), id("4")));

        assertThat(json.writeValueAsString(event))
                .isEqualTo(
                        "{\"dealId\":\"00000000-0000-7000-8000-000000000001\","
                                + "\"productId\":\"00000000-0000-7000-8000-000000000002\","
                                + "\"size\":3,\"recipientUserIds\":["
                                + "\"00000000-0000-7000-8000-000000000003\","
                                + "\"00000000-0000-7000-8000-000000000004\"]}");
    }

    @Test
    void teamExpiredV1JsonShapeIsStable() throws Exception {
        var event =
                new TeamExpiredV1(
                        id("1"), id("2"), Instant.parse("2026-10-12T00:00:00Z"), List.of(id("3")));

        assertThat(json.writeValueAsString(event))
                .isEqualTo(
                        "{\"dealId\":\"00000000-0000-7000-8000-000000000001\","
                                + "\"productId\":\"00000000-0000-7000-8000-000000000002\","
                                + "\"expiredAt\":\"2026-10-12T00:00:00Z\","
                                + "\"recipientUserIds\":["
                                + "\"00000000-0000-7000-8000-000000000003\"]}");
    }

    @Test
    void paymentSucceededV1JsonShapeIsStable() throws Exception {
        var event =
                new PaymentSucceededV1(
                        id("1"),
                        id("2"),
                        id("3"),
                        "JOIN",
                        id("4"),
                        Instant.parse("2026-10-12T00:00:00Z"));

        assertThat(json.writeValueAsString(event))
                .isEqualTo(
                        "{\"paymentId\":\"00000000-0000-7000-8000-000000000001\","
                                + "\"intentId\":\"00000000-0000-7000-8000-000000000002\","
                                + "\"recipientUserId\":\"00000000-0000-7000-8000-000000000003\","
                                + "\"operation\":\"JOIN\","
                                + "\"businessId\":\"00000000-0000-7000-8000-000000000004\","
                                + "\"confirmedAt\":\"2026-10-12T00:00:00Z\"}");
    }

    @Test
    void envelopeJsonShapeIsStable() throws Exception {
        var event =
                new IntegrationEventEnvelope<>(
                        id("1"),
                        "TeamSucceeded",
                        1,
                        Instant.parse("2026-10-12T00:00:00Z"),
                        "commerce-svc",
                        "trace-123",
                        id("2"),
                        new TeamSucceededV1(id("2"), id("3"), 2, List.of(id("4"))));

        assertThat(json.writeValueAsString(event))
                .isEqualTo(
                        "{\"eventId\":\"00000000-0000-7000-8000-000000000001\","
                                + "\"eventType\":\"TeamSucceeded\",\"version\":1,"
                                + "\"occurredAt\":\"2026-10-12T00:00:00Z\","
                                + "\"producer\":\"commerce-svc\",\"traceId\":\"trace-123\","
                                + "\"aggregateId\":\"00000000-0000-7000-8000-000000000002\","
                                + "\"payload\":{"
                                + "\"dealId\":\"00000000-0000-7000-8000-000000000002\","
                                + "\"productId\":\"00000000-0000-7000-8000-000000000003\","
                                + "\"size\":2,\"recipientUserIds\":["
                                + "\"00000000-0000-7000-8000-000000000004\"]}}");
    }

    @Test
    void teamRecipientListIsDefensivelyCopied() {
        var recipients = new java.util.ArrayList<>(List.of(UUID.randomUUID()));
        var event = new TeamSucceededV1(UUID.randomUUID(), UUID.randomUUID(), 2, recipients);
        recipients.clear();
        assertThat(event.recipientUserIds()).hasSize(1);
    }

    @Test
    void envelopeRejectsInvalidVersionAndOversizedTraceId() {
        UUID id = UUID.randomUUID();
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new IntegrationEventEnvelope<>(
                                id,
                                "TeamSucceeded",
                                0,
                                Instant.now(),
                                "commerce-svc",
                                "trace",
                                id,
                                new TeamSucceededV1(id, id, 2, List.of())));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new IntegrationEventEnvelope<>(
                                id,
                                "TeamSucceeded",
                                1,
                                Instant.now(),
                                "commerce-svc",
                                "x".repeat(65),
                                id,
                                new TeamSucceededV1(id, id, 2, List.of())));
    }

    @Test
    void payloadContractsRejectUnsupportedBusinessValues() {
        UUID id = id("1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TeamSucceededV1(id, id, 1, List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new PaymentSucceededV1(
                                id, id, id, "REFUND", id, Instant.parse("2026-10-12T00:00:00Z")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void envelopeRejectsMissingOrBlankEventTypeProducerAndTrace(String invalid) {
        UUID id = id("1");
        var payload = new TeamSucceededV1(id, id, 2, List.of());
        Instant occurredAt = Instant.parse("2026-10-12T00:00:00Z");
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new IntegrationEventEnvelope<>(
                                id, invalid, 1, occurredAt, "commerce-svc", "trace", id, payload));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new IntegrationEventEnvelope<>(
                                id, "TeamSucceeded", 1, occurredAt, invalid, "trace", id, payload));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new IntegrationEventEnvelope<>(
                                id,
                                "TeamSucceeded",
                                1,
                                occurredAt,
                                "commerce-svc",
                                invalid,
                                id,
                                payload));
    }

    private static UUID id(String suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + "0".repeat(11) + suffix);
    }
}
