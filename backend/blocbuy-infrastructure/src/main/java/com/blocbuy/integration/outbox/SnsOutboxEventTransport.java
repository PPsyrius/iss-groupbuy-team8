package com.blocbuy.integration.outbox;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry;
import tools.jackson.databind.ObjectMapper;

/** SNS protocol adapter for the shared lease-aware batch publisher. */
public final class SnsOutboxEventTransport
        extends BatchingOutboxEventTransport<PublishBatchRequestEntry> implements AutoCloseable {

    private final SnsClient client;
    private final String topicArn;
    private final ObjectMapper json;

    /** Preserves the sequential default for existing callers. */
    public SnsOutboxEventTransport(SnsClient client, String topicArn, ObjectMapper json) {
        this(client, topicArn, json, 1);
    }

    /** Bounds only SNS batch I/O; the client and its timeout budget remain externally owned. */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Spring owns the shared thread-safe SDK client and configured mapper; "
                            + "this transport neither reconfigures nor closes them")
    public SnsOutboxEventTransport(
            SnsClient client, String topicArn, ObjectMapper json, int concurrency) {
        super(new PublishingSettings(concurrency, true));
        this.client = client;
        this.topicArn = topicArn;
        this.json = json;
    }

    @Override
    public void close() {
        closePublisher();
    }

    @Override
    protected PublishBatchRequestEntry entry(OutboxEvent event) {
        var attributes = new HashMap<String, MessageAttributeValue>();
        OutboxMessageFactory.attributes(event)
                .forEach((name, value) ->
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
