package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blocbuy.common.gateway.OutboxEventTransport;
import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;
import software.amazon.awssdk.services.sns.model.PublishBatchResultEntry;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import tools.jackson.databind.json.JsonMapper;

class OutboxBatchFailureTest {

    enum Provider {
        SQS,
        SNS
    }

    @ParameterizedTest
    @EnumSource(Provider.class)
    void middleBatchExceptionPreservesSuccessesAndStillSendsLaterRows(Provider provider) {
        var calls = new AtomicInteger();
        var transport =
                transport(
                        provider,
                        ids -> {
                            if (calls.incrementAndGet() == 2) {
                                throw new IllegalStateException("middle batch unavailable");
                            }
                        });
        var events = SqsOutboxEventTransportTest.events(30);

        var results = transport.publish(events);

        assertThat(calls).hasValue(3);
        assertThat(results)
                .extracting(OutboxPublishResult::eventId)
                .containsExactlyElementsOf(events.stream().map(OutboxEvent::id).toList());
        assertThat(results.subList(0, 10)).allMatch(OutboxPublishResult::published);
        assertThat(results.subList(10, 20))
                .allMatch(result -> result.attempted() && !result.published());
        assertThat(results.subList(20, 30)).allMatch(OutboxPublishResult::published);
    }

    @ParameterizedTest
    @EnumSource(Provider.class)
    void serializationFailureOnlyConsumesTheInvalidRowsAttempt(Provider provider) {
        var sent = new ArrayList<String>();
        var events = new ArrayList<>(SqsOutboxEventTransportTest.events(11));
        events.set(3, invalid(events.get(3)));

        var results = transport(provider, sent::addAll).publish(events);

        assertThat(sent).hasSize(10).doesNotContain(events.get(3).id().toString());
        assertThat(results.get(3).published()).isFalse();
        assertThat(results.get(3).attempted()).isTrue();
        assertThat(results.stream().filter(OutboxPublishResult::published)).hasSize(10);
    }

    @ParameterizedTest
    @EnumSource(Provider.class)
    void entirelyInvalidBatchMakesNoProviderCall(Provider provider) {
        var calls = new AtomicInteger();
        var events =
                SqsOutboxEventTransportTest.events(10).stream()
                        .map(OutboxBatchFailureTest::invalid)
                        .toList();

        assertThat(transport(provider, ids -> calls.incrementAndGet()).publish(events))
                .hasSize(10)
                .allMatch(result -> result.attempted() && !result.published());
        assertThat(calls).hasValue(0);
    }

    @ParameterizedTest
    @EnumSource(Provider.class)
    void leaseLossDuringFirstSendSkipsAllSubsequentBatches(Provider provider) {
        var leaseValid = new AtomicBoolean(true);
        var calls = new AtomicInteger();
        var transport =
                transport(
                        provider,
                        ids -> {
                            calls.incrementAndGet();
                            leaseValid.set(false);
                        });

        var results = transport.publish(SqsOutboxEventTransportTest.events(30), leaseValid::get);

        assertThat(calls).hasValue(1);
        assertThat(results.subList(0, 10)).allMatch(OutboxPublishResult::published);
        assertThat(results.subList(10, 30))
                .allMatch(result -> !result.attempted() && !result.published());
    }

    @ParameterizedTest
    @EnumSource(Provider.class)
    void leaseLostBeforeTickSkipsEverything(Provider provider) {
        var calls = new AtomicInteger();

        assertThat(
                        transport(provider, ids -> calls.incrementAndGet())
                                .publish(SqsOutboxEventTransportTest.events(1), () -> false))
                .singleElement()
                .satisfies(result -> assertThat(result.attempted()).isFalse());
        assertThat(calls).hasValue(0);
    }

    @ParameterizedTest
    @EnumSource(Provider.class)
    void rechecksLeaseAfterPreparingEntriesBeforeSending(Provider provider) {
        var checks = new AtomicInteger();
        var calls = new AtomicInteger();

        assertThat(
                        transport(provider, ids -> calls.incrementAndGet())
                                .publish(
                                        SqsOutboxEventTransportTest.events(1),
                                        () -> checks.incrementAndGet() == 1))
                .singleElement()
                .satisfies(result -> assertThat(result.attempted()).isFalse());
        assertThat(calls).hasValue(0);
    }

    private static OutboxEvent invalid(OutboxEvent event) {
        return new OutboxEvent(
                event.id(),
                event.eventType(),
                event.eventVersion(),
                event.aggregateId(),
                "{not-json",
                event.traceId(),
                event.createdAt(),
                event.attempts());
    }

    private static OutboxEventTransport transport(Provider provider, Consumer<List<String>> send) {
        var json = JsonMapper.builder().build();
        if (provider == Provider.SQS) {
            var client = mock(SqsClient.class);
            when(client.sendMessageBatch(any(SendMessageBatchRequest.class)))
                    .thenAnswer(call -> {
                        SendMessageBatchRequest request = call.getArgument(0);
                        send.accept(request.entries().stream().map(entry -> entry.id()).toList());
                        return SendMessageBatchResponse.builder()
                                .successful(
                                        request.entries().stream()
                                                .map(entry ->
                                                        SendMessageBatchResultEntry.builder()
                                                                .id(entry.id())
                                                                .build())
                                                .toList())
                                .build();
                    });
            return new SqsOutboxEventTransport(client, "http://queue", json);
        }
        var client = mock(SnsClient.class);
        when(client.publishBatch(any(PublishBatchRequest.class)))
                .thenAnswer(call -> {
                    PublishBatchRequest request = call.getArgument(0);
                    send.accept(
                            request.publishBatchRequestEntries().stream()
                                    .map(entry -> entry.id())
                                    .toList());
                    return PublishBatchResponse.builder()
                            .successful(
                                    request.publishBatchRequestEntries().stream()
                                            .map(entry ->
                                                    PublishBatchResultEntry.builder()
                                                            .id(entry.id())
                                                            .build())
                                            .toList())
                            .build();
                });
        return new SnsOutboxEventTransport(
                client, "arn:aws:sns:us-east-1:000000000000:events", json);
    }
}
