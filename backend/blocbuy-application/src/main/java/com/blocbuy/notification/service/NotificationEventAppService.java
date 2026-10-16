package com.blocbuy.notification.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.notification.assembler.NotificationEventAssembler;
import com.blocbuy.notification.event.NotificationEventHandler;
import com.blocbuy.notification.exception.NotificationErrorCode;
import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.model.NotificationEvent;
import com.blocbuy.notification.repository.ConsumedMessageRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a consumed integration event exactly once per consumer (backend ADR 0007, #446): the
 * consumed-message marker and the notifications commit or roll back together, and the caller
 * deletes the SQS message only after this method returned.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventAppService implements NotificationEventHandler {
    /** Column width of {@code sys_consumed_message.consumer}. */
    public static final int MAX_CONSUMER_LENGTH = 64;

    private final ConsumedMessageRepository consumedMessages;
    private final NotificationAppService notifications;

    @Override
    @Transactional(timeout = 10)
    public ConsumeOutcome handle(String consumer, NotificationEvent event) {
        if (consumer == null
                || consumer.isBlank()
                || consumer.length() > MAX_CONSUMER_LENGTH
                || event == null) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_INVALID);
        }
        // Render before writing: an invalid effect fails without touching the database.
        List<Notification> effects =
                NotificationEventAssembler.toNotifications(event, Instant.now());
        if (!consumedMessages.markConsumed(event.eventId(), consumer)) {
            log.info(
                    "Integration event already consumed eventId={} type={} consumer={}",
                    event.eventId(),
                    event.type().code(),
                    consumer);
            return ConsumeOutcome.DUPLICATE;
        }
        if (effects.isEmpty()) {
            // Not poison: commerce published a fact nobody is to be told about. Marking it consumed
            // keeps redelivery cheap; the WARN makes a producer-side recipient bug visible.
            log.warn(
                    "Integration event has no recipients eventId={} type={} consumer={}",
                    event.eventId(),
                    event.type().code(),
                    consumer);
            return ConsumeOutcome.APPLIED;
        }
        int inserted = 0;
        for (int from = 0; from < effects.size(); from += NotificationAppService.MAX_BATCH_SIZE) {
            int to = Math.min(effects.size(), from + NotificationAppService.MAX_BATCH_SIZE);
            inserted += notifications.recordBatch(effects.subList(from, to));
        }
        log.info(
                "Integration event applied eventId={} type={} consumer={} recipients={} inserted={}",
                event.eventId(),
                event.type().code(),
                consumer,
                effects.size(),
                inserted);
        return ConsumeOutcome.APPLIED;
    }
}
