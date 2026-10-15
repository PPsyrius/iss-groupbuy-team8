package com.blocbuy.notification.messaging;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.events.contract.IntegrationEventEnvelope;
import com.blocbuy.events.contract.PaymentSucceededV1;
import com.blocbuy.events.contract.TeamExpiredV1;
import com.blocbuy.events.contract.TeamSucceededV1;
import com.blocbuy.notification.model.NotificationEvent;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Anti-corruption layer from the commerce integration contract to {@link NotificationEvent}.
 *
 * <p>Parsing is tolerant first and strict second: the body is read as a tree, {@code eventType} and
 * {@code version} are checked against the supported set, and only a supported pair is bound to its
 * {@code *V1} contract (whose constructors validate it). An unsupported pair is reported, not
 * rejected, so probes and future versions can be acknowledged without effect; anything else that
 * does not bind is malformed and must stay on the queue for redrive to the DLQ.
 */
public final class IntegrationEventDecoder {
    private static final String TEAM_SUCCEEDED = "TeamSucceeded";
    private static final String TEAM_EXPIRED = "TeamExpired";
    private static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";

    private final ObjectMapper json;
    private final Map<String, Function<JsonNode, NotificationEvent>> supported;

    @SuppressFBWarnings("EI_EXPOSE_REP2") // Spring-owned, thread-safe shared mapper.
    public IntegrationEventDecoder(ObjectMapper json) {
        this.json = json;
        this.supported =
                Map.of(
                        key(TEAM_SUCCEEDED, 1), this::teamSucceeded,
                        key(TEAM_EXPIRED, 1), this::teamExpired,
                        key(PAYMENT_SUCCEEDED, 1), this::paymentSucceeded);
    }

    /**
     * Decodes one message body.
     *
     * @throws MalformedIntegrationEventException if the body is not a usable envelope
     */
    public Decoded decode(String body) {
        JsonNode root;
        try {
            root = json.readTree(body == null ? "" : body);
        } catch (JacksonException exception) {
            throw new MalformedIntegrationEventException(null, "body is not JSON");
        }
        if (root == null || !root.isObject()) {
            throw new MalformedIntegrationEventException(null, "body is not a JSON object");
        }
        UUID eventId = eventIdOrNull(root);
        JsonNode type = root.path("eventType");
        JsonNode version = root.path("version");
        if (!type.isString() || type.stringValue().isBlank()) {
            throw new MalformedIntegrationEventException(eventId, "eventType is missing");
        }
        if (!version.isIntegralNumber() || !version.canConvertToInt()) {
            throw new MalformedIntegrationEventException(eventId, "version is not an integer");
        }
        var binder = supported.get(key(type.stringValue(), version.intValue()));
        if (binder == null) {
            return new Decoded(eventId, type.stringValue(), version.intValue(), null, null);
        }
        try {
            NotificationEvent event = binder.apply(root);
            return new Decoded(
                    event.eventId(),
                    type.stringValue(),
                    version.intValue(),
                    root.path("traceId").asString(),
                    event);
        } catch (JacksonException | IllegalArgumentException | BizException exception) {
            // Constructor validation failures arrive wrapped in a JacksonException; the message
            // can echo payload values, so only the exception type is reported.
            throw new MalformedIntegrationEventException(
                    eventId,
                    "envelope or payload does not match "
                            + key(type.stringValue(), version.intValue())
                            + " ("
                            + exception.getClass().getSimpleName()
                            + ")");
        }
    }

    private NotificationEvent teamSucceeded(JsonNode root) {
        IntegrationEventEnvelope<TeamSucceededV1> envelope = bind(root, TeamSucceededV1.class);
        TeamSucceededV1 payload = envelope.payload();
        return new NotificationEvent.TeamSucceeded(
                envelope.eventId(), payload.dealId(), payload.size(), payload.recipientUserIds());
    }

    private NotificationEvent teamExpired(JsonNode root) {
        IntegrationEventEnvelope<TeamExpiredV1> envelope = bind(root, TeamExpiredV1.class);
        TeamExpiredV1 payload = envelope.payload();
        return new NotificationEvent.TeamExpired(
                envelope.eventId(), payload.dealId(), payload.recipientUserIds());
    }

    private NotificationEvent paymentSucceeded(JsonNode root) {
        IntegrationEventEnvelope<PaymentSucceededV1> envelope =
                bind(root, PaymentSucceededV1.class);
        PaymentSucceededV1 payload = envelope.payload();
        return new NotificationEvent.PaymentSucceeded(
                envelope.eventId(),
                payload.paymentId(),
                payload.recipientUserId(),
                "START".equals(payload.operation()));
    }

    private <T> IntegrationEventEnvelope<T> bind(JsonNode root, Class<T> payloadType) {
        var type =
                json.getTypeFactory()
                        .constructParametricType(IntegrationEventEnvelope.class, payloadType);
        // Contracts evolve additively: unknown fields are ignored, missing required ones fail.
        return json.readerFor(type)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .readValue(root);
    }

    private static UUID eventIdOrNull(JsonNode root) {
        JsonNode id = root.path("eventId");
        if (!id.isString()) {
            return null;
        }
        try {
            return UUID.fromString(id.stringValue());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String key(String eventType, int version) {
        return eventType + " v" + version;
    }

    /**
     * Decoding result. {@code event} is {@code null} for an unsupported type or version, which the
     * consumer acknowledges without effect. {@code eventId} and {@code traceId} may be null there.
     */
    public record Decoded(
            UUID eventId, String eventType, int version, String traceId, NotificationEvent event) {
        public boolean supported() {
            return event != null;
        }
    }
}
