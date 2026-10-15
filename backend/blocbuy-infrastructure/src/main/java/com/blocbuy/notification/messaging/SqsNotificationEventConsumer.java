package com.blocbuy.notification.messaging;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.notification.event.NotificationEventHandler;
import com.blocbuy.notification.event.NotificationEventHandler.ConsumeOutcome;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Long-polling SQS consumer for notification events (backend ADR 0007, #446), on the AWS SDK v2
 * because Spring Cloud AWS has no Spring Boot 4.1 line.
 *
 * <p>One poller thread receives only as many messages as there are free workers (a semaphore of
 * {@code concurrency} permits), so nothing queues in memory. A message is deleted only after the
 * handler's transaction committed ({@code APPLIED}/{@code DUPLICATE}) or when its type/version is
 * unsupported; malformed and failing messages are left for the visibility timeout and, after {@code
 * maxReceiveCount} receives, the DLQ. The SQS {@code MessageId} is logged but never used for
 * idempotency.
 *
 * <p>Stopping ends receiving, waits up to {@code shutdownTimeout} for in-flight messages and then
 * makes any still unfinished ones visible again; idempotency makes their redelivery safe.
 */
@Slf4j
public class SqsNotificationEventConsumer implements SmartLifecycle, DisposableBean {
    static final String TRACE_ATTRIBUTE = "traceId";
    private static final int MAX_TRACE_ID_LENGTH = 64;
    private static final int SQS_BATCH_LIMIT = 10;

    private final SqsClient sqs;
    private final String queueUrl;
    private final IntegrationEventDecoder decoder;
    private final NotificationEventHandler handler;
    private final NotificationConsumerProperties settings;
    private final Semaphore freeWorkers;
    private final Set<Message> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ExecutorService workers;
    private volatile Thread poller;

    @SuppressFBWarnings("EI_EXPOSE_REP2") // Owns the client; the rest are shared thread-safe beans.
    public SqsNotificationEventConsumer(
            SqsClient sqs,
            String queueUrl,
            IntegrationEventDecoder decoder,
            NotificationEventHandler handler,
            NotificationConsumerProperties settings) {
        this.sqs = sqs;
        this.queueUrl = queueUrl;
        this.decoder = decoder;
        this.handler = handler;
        this.settings = settings;
        this.freeWorkers = new Semaphore(settings.concurrency());
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        AtomicInteger sequence = new AtomicInteger();
        workers =
                Executors.newFixedThreadPool(
                        settings.concurrency(),
                        task ->
                                new Thread(
                                        task,
                                        "notification-consumer-" + sequence.incrementAndGet()));
        poller = new Thread(this::pollUntilStopped, "notification-consumer-poller");
        poller.start();
        log.info(
                "Notification consumer started consumer={} concurrency={} waitTime={}"
                        + " visibilityTimeout={}",
                settings.name(),
                settings.concurrency(),
                settings.waitTime(),
                settings.visibilityTimeout());
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        poller.interrupt();
        workers.shutdown();
        boolean finished = false;
        try {
            finished =
                    workers.awaitTermination(
                            settings.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        if (!finished) {
            // Snapshot first: interrupted workers remove themselves from the in-flight set.
            List<Message> unfinished = List.copyOf(inFlight);
            workers.shutdownNow();
            log.warn(
                    "Notification consumer stopped with {} unfinished messages; releasing them",
                    unfinished.size());
            release(unfinished);
        }
        log.info("Notification consumer stopped consumer={}", settings.name());
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void destroy() {
        stop();
        sqs.close();
    }

    private void pollUntilStopped() {
        while (running.get()) {
            try {
                pollOnce();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** One receive call sized to the free workers, then dispatch. */
    void pollOnce() throws InterruptedException {
        freeWorkers.acquire();
        int granted = 1;
        while (granted < settings.maxMessages() && freeWorkers.tryAcquire()) {
            granted++;
        }
        List<Message> messages;
        try {
            messages = receive(granted);
        } catch (RuntimeException exception) {
            // Any failure, not only SdkException: a dead poller would stop consumption silently.
            freeWorkers.release(granted);
            if (!running.get()) {
                return;
            }
            log.warn(
                    "Notification consumer receive failed; retrying in {}",
                    settings.errorBackoff(),
                    exception);
            Thread.sleep(settings.errorBackoff().toMillis());
            return;
        }
        freeWorkers.release(granted - messages.size());
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (!running.get() || !dispatch(message)) {
                List<Message> rest = messages.subList(i, messages.size());
                freeWorkers.release(rest.size());
                release(rest);
                return;
            }
        }
    }

    private List<Message> receive(int maxMessages) {
        return sqs.receiveMessage(
                        ReceiveMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(maxMessages)
                                .waitTimeSeconds((int) settings.waitTime().toSeconds())
                                .visibilityTimeout((int) settings.visibilityTimeout().toSeconds())
                                .messageSystemAttributeNames(
                                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                                .messageAttributeNames("All")
                                .build())
                .messages();
    }

    private boolean dispatch(Message message) {
        inFlight.add(message);
        try {
            workers.execute(
                    () -> {
                        try {
                            process(message);
                        } finally {
                            inFlight.remove(message);
                            freeWorkers.release();
                        }
                    });
            return true;
        } catch (RejectedExecutionException exception) {
            inFlight.remove(message);
            return false;
        }
    }

    /** Handles one message and deletes it only when that is safe; never throws. */
    void process(Message message) {
        String receiveCount =
                message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        String traceId = sanitize(attribute(message, TRACE_ATTRIBUTE));
        MDC.put(ApiConstants.TRACE_ID_MDC_KEY, traceId == null ? newTraceId() : traceId);
        try {
            IntegrationEventDecoder.Decoded decoded = decoder.decode(message.body());
            if (traceId == null && sanitize(decoded.traceId()) != null) {
                MDC.put(ApiConstants.TRACE_ID_MDC_KEY, decoded.traceId());
            }
            if (!decoded.supported()) {
                log.warn(
                        "Acknowledging unsupported integration event without effect eventType={}"
                                + " version={} eventId={} messageId={}",
                        decoded.eventType(),
                        decoded.version(),
                        decoded.eventId(),
                        message.messageId());
                delete(message, decoded.eventId());
                return;
            }
            ConsumeOutcome outcome = handler.handle(settings.name(), decoded.event());
            delete(message, decoded.eventId());
            log.debug(
                    "Integration event acknowledged eventId={} outcome={} messageId={}",
                    decoded.eventId(),
                    outcome,
                    message.messageId());
        } catch (MalformedIntegrationEventException exception) {
            log.warn(
                    "Malformed integration event left for redrive messageId={} eventId={}"
                            + " receiveCount={} reason={}",
                    message.messageId(),
                    exception.eventId(),
                    receiveCount,
                    exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn(
                    "Integration event handling failed; left for retry messageId={}"
                            + " receiveCount={}",
                    message.messageId(),
                    receiveCount,
                    exception);
        } finally {
            MDC.remove(ApiConstants.TRACE_ID_MDC_KEY);
        }
    }

    private void delete(Message message, UUID eventId) {
        try {
            sqs.deleteMessage(
                    DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build());
        } catch (SdkException exception) {
            // Committed already: the redelivery is a DUPLICATE no-op.
            log.warn(
                    "Integration event delete failed; redelivery will be a duplicate eventId={}"
                            + " messageId={}",
                    eventId,
                    message.messageId(),
                    exception);
        }
    }

    /** Makes unprocessed messages visible again at once instead of after the timeout. */
    private void release(List<Message> messages) {
        for (int from = 0; from < messages.size(); from += SQS_BATCH_LIMIT) {
            List<ChangeMessageVisibilityBatchRequestEntry> entries = new ArrayList<>();
            List<Message> chunk =
                    messages.subList(from, Math.min(messages.size(), from + SQS_BATCH_LIMIT));
            for (int i = 0; i < chunk.size(); i++) {
                entries.add(
                        ChangeMessageVisibilityBatchRequestEntry.builder()
                                .id(Integer.toString(i))
                                .receiptHandle(chunk.get(i).receiptHandle())
                                .visibilityTimeout(0)
                                .build());
            }
            try {
                sqs.changeMessageVisibilityBatch(
                        ChangeMessageVisibilityBatchRequest.builder()
                                .queueUrl(queueUrl)
                                .entries(entries)
                                .build());
            } catch (SdkException exception) {
                log.warn(
                        "Could not release {} messages; they reappear after the visibility"
                                + " timeout",
                        entries.size(),
                        exception);
            }
        }
    }

    private static String attribute(Message message, String name) {
        Map<String, MessageAttributeValue> attributes = message.messageAttributes();
        MessageAttributeValue value = attributes == null ? null : attributes.get(name);
        return value == null ? null : value.stringValue();
    }

    /** Same rule as the HTTP {@code TraceIdFilter}: 1-64 of {@code [A-Za-z0-9-_.]}. */
    static String sanitize(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_TRACE_ID_LENGTH) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed =
                    (c >= 'a' && c <= 'z')
                            || (c >= 'A' && c <= 'Z')
                            || (c >= '0' && c <= '9')
                            || c == '-'
                            || c == '_'
                            || c == '.';
            if (!allowed) {
                return null;
            }
        }
        return value;
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
