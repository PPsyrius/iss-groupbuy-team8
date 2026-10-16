package com.blocbuy.integration.outbox;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.events.contract.IntegrationEventEnvelope;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class OutboxMessageFactory {

    private OutboxMessageFactory() {}

    static String body(OutboxEvent event, ObjectMapper json) {
        try {
            JsonNode payload = json.readTree(event.payloadJson());
            var envelope =
                    new IntegrationEventEnvelope<>(
                            event.id(),
                            event.eventType(),
                            event.eventVersion(),
                            event.createdAt(),
                            "blocbuy-commerce",
                            event.traceId(),
                            event.aggregateId(),
                            payload);
            return json.writeValueAsString(envelope);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize outbox event", exception);
        }
    }

    static Map<String, String> attributes(OutboxEvent event) {
        return Map.of(
                "eventType", event.eventType(),
                "version", Integer.toString(event.eventVersion()),
                "traceId", event.traceId());
    }
}
