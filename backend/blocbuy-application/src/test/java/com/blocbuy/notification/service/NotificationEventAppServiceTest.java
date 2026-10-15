package com.blocbuy.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.notification.event.NotificationEventHandler.ConsumeOutcome;
import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.model.NotificationEvent;
import com.blocbuy.notification.repository.ConsumedMessageRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationEventAppServiceTest {
    private static final String CONSUMER = "notification";
    private static final UUID EVENT = UUID.fromString("01990000-0000-7000-8000-000000000001");
    private static final UUID TEAM = UUID.fromString("01990000-0000-7000-8000-000000000002");
    private static final UUID A = UUID.fromString("01990000-0000-7000-8000-00000000000a");
    private static final UUID B = UUID.fromString("01990000-0000-7000-8000-00000000000b");

    private final ConsumedMessageRepository ledger = mock(ConsumedMessageRepository.class);
    private final NotificationAppService notifications = mock(NotificationAppService.class);
    private final NotificationEventAppService service =
            new NotificationEventAppService(ledger, notifications);

    @Test
    void firstDeliveryMarksTheEventAndRecordsOneNotificationPerRecipient() {
        when(ledger.markConsumed(EVENT, CONSUMER)).thenReturn(true);
        when(notifications.recordBatch(anyList())).thenReturn(2);

        assertThat(service.handle(CONSUMER, teamSucceeded(List.of(A, B))))
                .isEqualTo(ConsumeOutcome.APPLIED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> batch = ArgumentCaptor.forClass(List.class);
        verify(notifications).recordBatch(batch.capture());
        assertThat(batch.getValue()).extracting(Notification::userId).containsExactly(A, B);
        assertThat(batch.getValue()).extracting(Notification::eventId).containsOnly(EVENT);
    }

    @Test
    void duplicateEventWritesNothing() {
        when(ledger.markConsumed(EVENT, CONSUMER)).thenReturn(false);

        assertThat(service.handle(CONSUMER, teamSucceeded(List.of(A))))
                .isEqualTo(ConsumeOutcome.DUPLICATE);

        verifyNoInteractions(notifications);
    }

    @Test
    void emptyRecipientListIsConsumedWithoutNotifications() {
        when(ledger.markConsumed(EVENT, CONSUMER)).thenReturn(true);

        assertThat(
                        service.handle(
                                CONSUMER,
                                new NotificationEvent.TeamExpired(EVENT, TEAM, List.of())))
                .isEqualTo(ConsumeOutcome.APPLIED);

        verify(ledger).markConsumed(EVENT, CONSUMER);
        verifyNoInteractions(notifications);
    }

    @Test
    void largeRecipientListsAreWrittenInBoundedBatchesInOneCall() {
        List<UUID> recipients = new ArrayList<>();
        IntStream.range(0, NotificationAppService.MAX_BATCH_SIZE + 1)
                .forEach(i -> recipients.add(UUID.randomUUID()));
        when(ledger.markConsumed(EVENT, CONSUMER)).thenReturn(true);
        when(notifications.recordBatch(anyList()))
                .thenAnswer(call -> call.<List<?>>getArgument(0).size());

        assertThat(service.handle(CONSUMER, teamSucceeded(recipients)))
                .isEqualTo(ConsumeOutcome.APPLIED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> batches = ArgumentCaptor.forClass(List.class);
        verify(notifications, times(2)).recordBatch(batches.capture());
        assertThat(batches.getAllValues())
                .extracting(List::size)
                .containsExactly(NotificationAppService.MAX_BATCH_SIZE, 1);
    }

    @Test
    void invalidConsumerOrMissingEventFailsBeforeAnyWrite() {
        var event = teamSucceeded(List.of(A));
        for (String consumer : new String[] {null, " ", "x".repeat(65)}) {
            assertThatThrownBy(() -> service.handle(consumer, event))
                    .isInstanceOf(BizException.class);
        }
        assertThatThrownBy(() -> service.handle(CONSUMER, null)).isInstanceOf(BizException.class);
        verify(ledger, never()).markConsumed(any(), any());
        verifyNoInteractions(notifications);
        when(ledger.markConsumed(EVENT, "x".repeat(64))).thenReturn(false);
        assertThat(service.handle("x".repeat(64), event)).isEqualTo(ConsumeOutcome.DUPLICATE);
    }

    @Test
    void failingWritePropagatesSoTheTransactionRollsBackAndTheMessageIsRetried() {
        when(ledger.markConsumed(EVENT, CONSUMER)).thenReturn(true);
        when(notifications.recordBatch(anyList())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.handle(CONSUMER, teamSucceeded(List.of(A))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static NotificationEvent teamSucceeded(List<UUID> recipients) {
        return new NotificationEvent.TeamSucceeded(EVENT, TEAM, 2, recipients);
    }
}
