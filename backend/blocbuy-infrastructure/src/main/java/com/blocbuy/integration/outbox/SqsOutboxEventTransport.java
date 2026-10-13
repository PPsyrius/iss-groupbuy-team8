package com.blocbuy.integration.outbox;

import com.blocbuy.common.gateway.OutboxEventTransport;
import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import tools.jackson.databind.ObjectMapper;

/** SQS adapter; API requests are split into the service's ten-entry batch limit. */
@RequiredArgsConstructor
public class SqsOutboxEventTransport implements OutboxEventTransport {

    private static final int PROVIDER_BATCH_SIZE = 10;

    private final SqsClient client;
    private final String queueUrl;
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
                                            SendMessageBatchRequestEntry.builder()
                                                    .id(event.id().toString())
                                                    .messageBody(
                                                            OutboxMessageFactory.body(event, json))
                                                    .messageAttributes(messageAttributes(event))
                                                    .build())
                            .toList();
            var response =
                    client.sendMessageBatch(
                            SendMessageBatchRequest.builder()
                                    .queueUrl(queueUrl)
                                    .entries(entries)
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
                                        failureReasons.getOrDefault(id, "SQS batch failure")));
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
