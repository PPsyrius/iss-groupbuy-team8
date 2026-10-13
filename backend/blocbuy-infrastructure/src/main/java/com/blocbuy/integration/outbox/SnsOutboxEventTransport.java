package com.blocbuy.integration.outbox;

import com.blocbuy.common.gateway.OutboxEventTransport;
import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry;
import tools.jackson.databind.ObjectMapper;

/** SNS adapter; API requests are split into the service's ten-entry batch limit. */
@RequiredArgsConstructor
public class SnsOutboxEventTransport implements OutboxEventTransport {

    private static final int PROVIDER_BATCH_SIZE = 10;

    private final SnsClient client;
    private final String topicArn;
    private final ObjectMapper json;

    @Override
    public List<OutboxPublishResult> publish(List<OutboxEvent> events) {
        List<OutboxPublishResult> results = new ArrayList<>(events.size());
        for (int start = 0; start < events.size(); start += PROVIDER_BATCH_SIZE) {
            List<OutboxEvent> batch =
                    events.subList(start, Math.min(start + PROVIDER_BATCH_SIZE, events.size()));
            var entries =
                    batch.stream()
                            .map(
                                    event ->
                                            PublishBatchRequestEntry.builder()
                                                    .id(event.id().toString())
                                                    .message(OutboxMessageFactory.body(event, json))
                                                    .messageAttributes(messageAttributes(event))
                                                    .build())
                            .toList();
            var response =
                    client.publishBatch(
                            PublishBatchRequest.builder()
                                    .topicArn(topicArn)
                                    .publishBatchRequestEntries(entries)
                                    .build());
            Set<String> succeeded = new HashSet<>();
            response.successful().forEach(entry -> succeeded.add(entry.id()));
            var failureReasons = new java.util.HashMap<String, String>();
            response.failed().forEach(entry -> failureReasons.put(entry.id(), entry.code()));
            for (OutboxEvent event : batch) {
                String id = event.id().toString();
                results.add(
                        succeeded.contains(id)
                                ? OutboxPublishResult.published(event.id())
                                : OutboxPublishResult.failed(
                                        event.id(),
                                        failureReasons.getOrDefault(id, "SNS batch failure")));
            }
        }
        return results;
    }

    private static java.util.Map<String, MessageAttributeValue> messageAttributes(
            OutboxEvent event) {
        var values = new java.util.HashMap<String, MessageAttributeValue>();
        OutboxMessageFactory.attributes(event)
                .forEach(
                        (name, value) ->
                                values.put(
                                        name,
                                        MessageAttributeValue.builder()
                                                .dataType("String")
                                                .stringValue(value)
                                                .build()));
        return values;
    }
}
