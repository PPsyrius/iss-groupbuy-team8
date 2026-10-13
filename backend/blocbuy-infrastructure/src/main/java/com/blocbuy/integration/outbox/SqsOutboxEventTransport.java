package com.blocbuy.integration.outbox;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import tools.jackson.databind.ObjectMapper;

/** SQS protocol adapter for the shared lease-aware batch publisher. */
@RequiredArgsConstructor
public class SqsOutboxEventTransport
        extends BatchingOutboxEventTransport<SendMessageBatchRequestEntry> {

    private final SqsClient client;
    private final String queueUrl;
    private final ObjectMapper json;

    @Override
    protected SendMessageBatchRequestEntry entry(OutboxEvent event) {
        var attributes = new HashMap<String, MessageAttributeValue>();
        OutboxMessageFactory.attributes(event)
                .forEach(
                        (name, value) ->
                                attributes.put(
                                        name,
                                        MessageAttributeValue.builder()
                                                .dataType("String")
                                                .stringValue(value)
                                                .build()));
        return SendMessageBatchRequestEntry.builder()
                .id(event.id().toString())
                .messageBody(OutboxMessageFactory.body(event, json))
                .messageAttributes(attributes)
                .build();
    }

    @Override
    protected List<OutboxPublishResult> send(
            List<OutboxEvent> events, List<SendMessageBatchRequestEntry> entries) {
        var response =
                client.sendMessageBatch(
                        SendMessageBatchRequest.builder()
                                .queueUrl(queueUrl)
                                .entries(entries)
                                .build());
        var succeeded = new HashSet<String>();
        response.successful().forEach(entry -> succeeded.add(entry.id()));
        var failures = new HashMap<String, String>();
        response.failed().forEach(entry -> failures.put(entry.id(), entry.code()));
        return results(events, succeeded, failures, "SQS batch failure");
    }
}
