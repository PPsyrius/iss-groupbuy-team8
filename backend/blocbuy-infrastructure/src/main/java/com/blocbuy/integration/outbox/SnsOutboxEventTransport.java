package com.blocbuy.integration.outbox;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry;
import tools.jackson.databind.ObjectMapper;

/** SNS protocol adapter for the shared lease-aware batch publisher. */
@RequiredArgsConstructor
public class SnsOutboxEventTransport
        extends BatchingOutboxEventTransport<PublishBatchRequestEntry> {

    private final SnsClient client;
    private final String topicArn;
    private final ObjectMapper json;

    @Override
    protected PublishBatchRequestEntry entry(OutboxEvent event) {
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
        return PublishBatchRequestEntry.builder()
                .id(event.id().toString())
                .message(OutboxMessageFactory.body(event, json))
                .messageAttributes(attributes)
                .build();
    }

    @Override
    protected List<OutboxPublishResult> send(
            List<OutboxEvent> events, List<PublishBatchRequestEntry> entries) {
        var response =
                client.publishBatch(
                        PublishBatchRequest.builder()
                                .topicArn(topicArn)
                                .publishBatchRequestEntries(entries)
                                .build());
        var succeeded = new HashSet<String>();
        response.successful().forEach(entry -> succeeded.add(entry.id()));
        var failures = new HashMap<String, String>();
        response.failed().forEach(entry -> failures.put(entry.id(), entry.code()));
        return results(events, succeeded, failures, "SNS batch failure");
    }
}
