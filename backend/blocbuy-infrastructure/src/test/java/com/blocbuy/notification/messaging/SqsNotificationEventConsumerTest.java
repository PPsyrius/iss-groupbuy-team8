package com.blocbuy.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.events.contract.IntegrationEventEnvelope;
import com.blocbuy.events.contract.PaymentSucceededV1;
import com.blocbuy.notification.event.NotificationEventHandler;
import com.blocbuy.notification.event.NotificationEventHandler.ConsumeOutcome;
import com.blocbuy.notification.model.NotificationEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.json.JsonMapper;

class SqsNotificationEventConsumerTest {
    private static final String QUEUE = "http://sqs.local/000000000000/notification-events";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID EVENT = UUID.fromString("01990000-0000-7000-8000-000000000001");
    private static final UUID PAYMENT = UUID.fromString("01990000-0000-7000-8000-000000000002");
    private static final UUID USER = UUID.fromString("01990000-0000-7000-8000-00000000000a");

    private final SqsClient sqs = mock(SqsClient.class);
    private final NotificationEventHandler handler = mock(NotificationEventHandler.class);
    private SqsNotificationEventConsumer consumer = consumer(2, Duration.ofSeconds(5));

    @AfterEach
    void stopConsumer() {
        consumer.stop();
    }

    @Test
    void appliedEventIsDeletedAfterTheHandlerReturnsWithTheTraceIdRestored() {
        AtomicReference<String> traceDuringHandling = new AtomicReference<>();
        when(handler.handle(eq("notification"), any()))
                .thenAnswer(
                        call -> {
                            traceDuringHandling.set(MDC.get(ApiConstants.TRACE_ID_MDC_KEY));
                            return ConsumeOutcome.APPLIED;
                        });

        consumer.process(message("m-1", "r-1", valid(), "attr-trace.1"));

        verify(handler)
                .handle(
                        "notification",
                        new NotificationEvent.PaymentSucceeded(EVENT, PAYMENT, USER, true));
        assertThat(deletedHandles()).containsExactly("r-1");
        assertThat(traceDuringHandling.get()).isEqualTo("attr-trace.1");
        assertThat(MDC.get(ApiConstants.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void duplicateIsDeletedAndInvalidTraceAttributeFallsBackToTheEnvelope() {
        AtomicReference<String> trace = new AtomicReference<>();
        when(handler.handle(any(), any()))
                .thenAnswer(
                        call -> {
                            trace.set(MDC.get(ApiConstants.TRACE_ID_MDC_KEY));
                            return ConsumeOutcome.DUPLICATE;
                        });

        consumer.process(message("m-2", "r-2", valid(), "bad trace\n"));

        assertThat(deletedHandles()).containsExactly("r-2");
        assertThat(trace.get()).isEqualTo("envelope-trace");
    }

    @Test
    void missingTracesGetAFreshOne() {
        AtomicReference<String> trace = new AtomicReference<>();
        when(handler.handle(any(), any()))
                .thenAnswer(
                        call -> {
                            trace.set(MDC.get(ApiConstants.TRACE_ID_MDC_KEY));
                            return ConsumeOutcome.APPLIED;
                        });
        var root = (tools.jackson.databind.node.ObjectNode) JSON.readTree(valid());
        root.put("traceId", "has space");

        consumer.process(message("m-3", "r-3", root.toString(), null));

        assertThat(trace.get()).isNotBlank().hasSize(36);
        assertThat(deletedHandles()).containsExactly("r-3");
    }

    @Test
    void unsupportedVersionIsAcknowledgedWithoutEffect() {
        String probe =
                "{\"eventId\":\""
                        + EVENT
                        + "\",\"eventType\":\"PaymentSucceeded\",\"version\":2147483647,"
                        + "\"payload\":{}}";

        consumer.process(message("m-4", "r-4", probe, "probe"));

        verifyNoInteractions(handler);
        assertThat(deletedHandles()).containsExactly("r-4");
    }

    @Test
    void malformedAndFailingMessagesAreNeverDeleted() {
        consumer.process(
                message("m-5", "r-5", "{\"eventType\":\"PaymentSucceeded\",\"version\":1}", null));
        verifyNoInteractions(handler);

        when(handler.handle(any(), any())).thenThrow(new IllegalStateException("db down"));
        consumer.process(message("m-6", "r-6", valid(), "t"));

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
        assertThat(MDC.get(ApiConstants.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void unexpectedDecoderFailureLeavesTheMessage() {
        IntegrationEventDecoder failing = mock(IntegrationEventDecoder.class);
        when(failing.decode(any())).thenThrow(new IllegalStateException("decoder bug"));
        var withFailingDecoder =
                new SqsNotificationEventConsumer(
                        sqs,
                        QUEUE,
                        failing,
                        handler,
                        new NotificationConsumerProperties(
                                true,
                                "notification",
                                1,
                                10,
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(1),
                                Duration.ofMillis(10)));

        withFailingDecoder.process(message("m-10", "r-10", valid(), "t"));

        verifyNoInteractions(handler);
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void failedDeleteAfterCommitIsLoggedNotThrown() {
        when(handler.handle(any(), any())).thenReturn(ConsumeOutcome.APPLIED);
        when(sqs.deleteMessage(any(DeleteMessageRequest.class)))
                .thenThrow(SdkClientException.create("network"));

        consumer.process(message("m-7", "r-7", valid(), "t"));

        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void runningConsumerReceivesWithTheConfiguredRequestAndDeletesHandledMessages()
            throws Exception {
        when(handler.handle(any(), any())).thenReturn(ConsumeOutcome.APPLIED);
        AtomicInteger calls = new AtomicInteger();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(
                        call -> {
                            if (calls.incrementAndGet() == 1) {
                                throw SdkClientException.create("broker unavailable");
                            }
                            if (calls.get() == 2) {
                                return response(message("m-8", "r-8", valid(), "t"));
                            }
                            return emptyLongPoll();
                        });

        consumer.start();
        consumer.start(); // idempotent
        assertThat(consumer.isRunning()).isTrue();

        verify(sqs, timeout(5000)).deleteMessage(any(DeleteMessageRequest.class));
        ArgumentCaptor<ReceiveMessageRequest> request =
                ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqs, timeout(5000).atLeast(2)).receiveMessage(request.capture());
        ReceiveMessageRequest first = request.getAllValues().get(0);
        assertThat(first.queueUrl()).isEqualTo(QUEUE);
        assertThat(first.maxNumberOfMessages()).isEqualTo(2);
        assertThat(first.waitTimeSeconds()).isEqualTo(1);
        assertThat(first.visibilityTimeout()).isEqualTo(30);
        assertThat(first.messageSystemAttributeNames())
                .containsExactly(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        assertThat(first.messageAttributeNames()).containsExactly("All");

        consumer.stop();
        assertThat(consumer.isRunning()).isFalse();
        consumer.stop(); // idempotent
    }

    @Test
    void stopReleasesMessagesStillInFlightAfterTheShutdownTimeout() throws Exception {
        consumer = consumer(1, Duration.ofMillis(100));
        CountDownLatch handling = new CountDownLatch(1);
        CountDownLatch blocker = new CountDownLatch(1);
        when(handler.handle(any(), any()))
                .thenAnswer(
                        call -> {
                            handling.countDown();
                            try {
                                blocker.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                            throw new IllegalStateException("interrupted mid-transaction");
                        });
        AtomicInteger calls = new AtomicInteger();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(
                        call -> {
                            if (calls.incrementAndGet() == 1) {
                                return response(message("m-9", "r-9", valid(), "t"));
                            }
                            return emptyLongPoll();
                        });
        consumer.start();
        assertThat(handling.await(5, TimeUnit.SECONDS)).isTrue();

        consumer.stop();

        ArgumentCaptor<ChangeMessageVisibilityBatchRequest> release =
                ArgumentCaptor.forClass(ChangeMessageVisibilityBatchRequest.class);
        verify(sqs).changeMessageVisibilityBatch(release.capture());
        assertThat(release.getValue().entries())
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.receiptHandle()).isEqualTo("r-9");
                            assertThat(entry.visibilityTimeout()).isZero();
                        });
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void messagesALongPollReturnsAfterStopAreReleasedAtOnceFromTheUninterruptedPoller()
            throws Exception {
        consumer = consumer(64, Duration.ofSeconds(1));
        AtomicBoolean interrupted = longPollThatReturnsTenMessagesAfterStop();

        ArgumentCaptor<ChangeMessageVisibilityBatchRequest> release =
                ArgumentCaptor.forClass(ChangeMessageVisibilityBatchRequest.class);
        verify(sqs, timeout(5000)).changeMessageVisibilityBatch(release.capture());
        assertThat(interrupted.get()).isFalse();
        assertThat(release.getValue().queueUrl()).isEqualTo(QUEUE);
        assertThat(release.getValue().entries())
                .hasSize(10)
                .allSatisfy(entry -> assertThat(entry.visibilityTimeout()).isZero());
        verifyNoInteractions(handler);
        verify(sqs, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void failedReleaseAfterStopIsLoggedAndThePollerStillExits() throws Exception {
        consumer = consumer(64, Duration.ofSeconds(1));
        when(sqs.changeMessageVisibilityBatch(any(ChangeMessageVisibilityBatchRequest.class)))
                .thenThrow(SdkClientException.create("closed"));
        longPollThatReturnsTenMessagesAfterStop();

        verify(sqs, timeout(5000))
                .changeMessageVisibilityBatch(any(ChangeMessageVisibilityBatchRequest.class));
        consumer.destroy(); // joins the poller, then closes the client
        verify(sqs).close();
        verifyNoInteractions(handler);
    }

    /** Starts the consumer, stops it while a receive is open, then lets that receive return. */
    private AtomicBoolean longPollThatReturnsTenMessagesAfterStop() throws Exception {
        var messages =
                IntStream.range(0, 10)
                        .mapToObj(i -> message("m" + i, "r" + i, valid(), "t"))
                        .toList();
        CountDownLatch polling = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(
                        call -> {
                            polling.countDown();
                            if (!stopped.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("stop never happened");
                            }
                            interrupted.set(Thread.currentThread().isInterrupted());
                            return response(messages);
                        });
        consumer.start();
        assertThat(polling.await(5, TimeUnit.SECONDS)).isTrue();
        consumer.stop(); // returns while the long poll is still open
        assertThat(consumer.isRunning()).isFalse();
        stopped.countDown();
        return interrupted;
    }

    @Test
    void destroyStopsAndClosesTheOwnedClient() {
        consumer.destroy();
        verify(sqs).close();
    }

    @Test
    void traceIdsFollowTheHttpFilterRule() {
        assertThat(SqsNotificationEventConsumer.sanitize("Abc-1_2.z")).isEqualTo("Abc-1_2.z");
        assertThat(SqsNotificationEventConsumer.sanitize(null)).isNull();
        assertThat(SqsNotificationEventConsumer.sanitize(" ")).isNull();
        assertThat(SqsNotificationEventConsumer.sanitize("a".repeat(65))).isNull();
        assertThat(SqsNotificationEventConsumer.sanitize("a/b")).isNull();
        assertThat(SqsNotificationEventConsumer.sanitize("é")).isEqualTo("é");
        assertThat(SqsNotificationEventConsumer.sanitize("a b")).isNull();
        assertThat(SqsNotificationEventConsumer.sanitize("a" + (char) 10 + "b")).isNull();
        assertThat(SqsNotificationEventConsumer.loggable("TeamSucceeded"))
                .isEqualTo("TeamSucceeded");
        assertThat(SqsNotificationEventConsumer.loggable("x".repeat(65))).isEqualTo("(invalid)");
        assertThat(SqsNotificationEventConsumer.sanitize("a".repeat(64))).hasSize(64);
    }

    private SqsNotificationEventConsumer consumer(int concurrency, Duration shutdownTimeout) {
        return new SqsNotificationEventConsumer(
                sqs,
                QUEUE,
                new IntegrationEventDecoder(JSON),
                handler,
                new NotificationConsumerProperties(
                        true,
                        "notification",
                        concurrency,
                        10,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(30),
                        shutdownTimeout,
                        Duration.ofMillis(10)));
    }

    private List<String> deletedHandles() {
        ArgumentCaptor<DeleteMessageRequest> deleted =
                ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqs, org.mockito.Mockito.atLeast(0)).deleteMessage(deleted.capture());
        deleted.getAllValues().forEach(request -> assertThat(request.queueUrl()).isEqualTo(QUEUE));
        return deleted.getAllValues().stream().map(DeleteMessageRequest::receiptHandle).toList();
    }

    private static String valid() {
        return JSON.writeValueAsString(
                new IntegrationEventEnvelope<>(
                        EVENT,
                        "PaymentSucceeded",
                        1,
                        Instant.parse("2026-10-16T08:00:00Z"),
                        "blocbuy-commerce",
                        "envelope-trace",
                        PAYMENT,
                        new PaymentSucceededV1(
                                PAYMENT,
                                UUID.randomUUID(),
                                USER,
                                "START",
                                UUID.randomUUID(),
                                Instant.parse("2026-10-16T08:00:00Z"))));
    }

    private static Message message(String id, String handle, String body, String trace) {
        var builder =
                Message.builder()
                        .messageId(id)
                        .receiptHandle(handle)
                        .body(body)
                        .attributes(
                                Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "1"));
        if (trace != null) {
            builder.messageAttributes(
                    Map.of(
                            SqsNotificationEventConsumer.TRACE_ATTRIBUTE,
                            MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(trace)
                                    .build()));
        }
        return builder.build();
    }

    private static ReceiveMessageResponse response(Message... messages) {
        return response(List.of(messages));
    }

    private static ReceiveMessageResponse response(List<Message> messages) {
        return ReceiveMessageResponse.builder().messages(messages).build();
    }

    /** An empty long poll that takes a little time, so the poller loop does not spin. */
    private static ReceiveMessageResponse emptyLongPoll() {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        return response();
    }
}
