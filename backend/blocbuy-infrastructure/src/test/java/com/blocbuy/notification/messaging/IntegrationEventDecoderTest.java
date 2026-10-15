package com.blocbuy.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.events.contract.IntegrationEventEnvelope;
import com.blocbuy.events.contract.PaymentSucceededV1;
import com.blocbuy.events.contract.TeamExpiredV1;
import com.blocbuy.events.contract.TeamSucceededV1;
import com.blocbuy.notification.model.NotificationEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class IntegrationEventDecoderTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID EVENT = UUID.fromString("01990000-0000-7000-8000-000000000001");
    private static final UUID TEAM = UUID.fromString("01990000-0000-7000-8000-000000000002");
    private static final UUID PAYMENT = UUID.fromString("01990000-0000-7000-8000-000000000003");
    private static final UUID A = UUID.fromString("01990000-0000-7000-8000-00000000000a");
    private static final UUID B = UUID.fromString("01990000-0000-7000-8000-00000000000b");
    private static final Instant AT = Instant.parse("2026-10-16T08:00:00Z");

    private final IntegrationEventDecoder decoder = new IntegrationEventDecoder(JSON);

    @Test
    void decodesTeamSucceededV1AsProducedByTheOutbox() {
        var decoded =
                decoder.decode(
                        envelope(
                                "TeamSucceeded",
                                1,
                                new TeamSucceededV1(TEAM, UUID.randomUUID(), 3, List.of(A, B))));
        assertThat(decoded.supported()).isTrue();
        assertThat(decoded.eventId()).isEqualTo(EVENT);
        assertThat(decoded.eventType()).isEqualTo("TeamSucceeded");
        assertThat(decoded.version()).isEqualTo(1);
        assertThat(decoded.traceId()).isEqualTo("trace-1");
        assertThat(decoded.event())
                .isEqualTo(new NotificationEvent.TeamSucceeded(EVENT, TEAM, 3, List.of(A, B)));
    }

    @Test
    void decodesTeamExpiredAndBothPaymentOperations() {
        assertThat(
                        decoder.decode(
                                        envelope(
                                                "TeamExpired",
                                                1,
                                                new TeamExpiredV1(
                                                        TEAM, UUID.randomUUID(), AT, List.of(A))))
                                .event())
                .isEqualTo(new NotificationEvent.TeamExpired(EVENT, TEAM, List.of(A)));
        assertThat(decoder.decode(envelope("PaymentSucceeded", 1, payment("START"))).event())
                .isEqualTo(new NotificationEvent.PaymentSucceeded(EVENT, PAYMENT, A, true));
        assertThat(decoder.decode(envelope("PaymentSucceeded", 1, payment("JOIN"))).event())
                .isEqualTo(new NotificationEvent.PaymentSucceeded(EVENT, PAYMENT, A, false));
    }

    @Test
    void additiveFieldsAreIgnored() {
        ObjectNode root = tree(envelope("PaymentSucceeded", 1, payment("JOIN")));
        root.put("newEnvelopeField", "x");
        ((ObjectNode) root.get("payload")).put("newPayloadField", 42);
        assertThat(decoder.decode(root.toString()).supported()).isTrue();
    }

    @Test
    void unsupportedVersionProbeAndUnknownTypeAreReportedNotRejected() {
        // infra/swe5001/scripts/messaging-check.sh probe: maximum version with an empty payload.
        String probe =
                "{\"eventId\":\""
                        + EVENT
                        + "\",\"eventType\":\"PaymentSucceeded\",\"version\":2147483647,"
                        + "\"payload\":{}}";
        var decoded = decoder.decode(probe);
        assertThat(decoded.supported()).isFalse();
        assertThat(decoded.event()).isNull();
        assertThat(decoded.eventId()).isEqualTo(EVENT);
        assertThat(decoded.version()).isEqualTo(Integer.MAX_VALUE);

        var unknown = decoder.decode("{\"eventType\":\"OrderShipped\",\"version\":1}");
        assertThat(unknown.supported()).isFalse();
        assertThat(unknown.eventId()).isNull();
        assertThat(unknown.eventType()).isEqualTo("OrderShipped");
        assertThat(decoder.decode("{\"eventType\":\"TeamSucceeded\",\"version\":2}").supported())
                .isFalse();
    }

    @Test
    void unreadableEnvelopesAreMalformed() {
        assertMalformed(null, null);
        assertMalformed("not json", null);
        assertMalformed("[1,2]", null);
        assertMalformed("\"text\"", null);
        assertMalformed("{\"version\":1}", null);
        assertMalformed("{\"eventType\":\" \",\"version\":1}", null);
        assertMalformed("{\"eventId\":\"nope\",\"eventType\":\"TeamSucceeded\"}", null);
        assertMalformed(
                "{\"eventId\":\"" + EVENT + "\",\"eventType\":\"TeamSucceeded\",\"version\":\"1\"}",
                EVENT);
        assertMalformed(
                "{\"eventId\":1,\"eventType\":\"TeamSucceeded\",\"version\":9999999999}", null);
    }

    @Test
    void supportedTypesWithAnInvalidPayloadOrEnvelopeAreMalformed() {
        ObjectNode badOperation = tree(envelope("PaymentSucceeded", 1, payment("START")));
        ((ObjectNode) badOperation.get("payload")).put("operation", "REFUND");
        assertMalformed(badOperation.toString(), EVENT);

        ObjectNode missingRecipient = tree(envelope("PaymentSucceeded", 1, payment("JOIN")));
        ((ObjectNode) missingRecipient.get("payload")).remove("recipientUserId");
        assertMalformed(missingRecipient.toString(), EVENT);

        ObjectNode tooSmall =
                tree(
                        envelope(
                                "TeamSucceeded",
                                1,
                                new TeamSucceededV1(TEAM, UUID.randomUUID(), 2, List.of(A))));
        ((ObjectNode) tooSmall.get("payload")).put("size", 1);
        assertMalformed(tooSmall.toString(), EVENT);

        ObjectNode nullRecipient =
                tree(
                        envelope(
                                "TeamExpired",
                                1,
                                new TeamExpiredV1(TEAM, UUID.randomUUID(), AT, List.of(A))));
        ((ObjectNode) nullRecipient.get("payload")).putArray("recipientUserIds").addNull();
        assertMalformed(nullRecipient.toString(), EVENT);

        ObjectNode noTrace = tree(envelope("PaymentSucceeded", 1, payment("JOIN")));
        noTrace.remove("traceId");
        assertMalformed(noTrace.toString(), EVENT);

        ObjectNode emptyPayload = tree(envelope("PaymentSucceeded", 1, payment("JOIN")));
        emptyPayload.putObject("payload");
        assertMalformed(emptyPayload.toString(), EVENT);
    }

    private void assertMalformed(String body, UUID expectedEventId) {
        assertThatThrownBy(() -> decoder.decode(body))
                .isInstanceOfSatisfying(
                        MalformedIntegrationEventException.class,
                        failure -> assertThat(failure.eventId()).isEqualTo(expectedEventId));
    }

    private static PaymentSucceededV1 payment(String operation) {
        return new PaymentSucceededV1(PAYMENT, UUID.randomUUID(), A, operation, TEAM, AT);
    }

    private static String envelope(String type, int version, Object payload) {
        return JSON.writeValueAsString(
                new IntegrationEventEnvelope<>(
                        EVENT, type, version, AT, "blocbuy-commerce", "trace-1", TEAM, payload));
    }

    private static ObjectNode tree(String body) {
        return (ObjectNode) JSON.readTree(body);
    }
}
