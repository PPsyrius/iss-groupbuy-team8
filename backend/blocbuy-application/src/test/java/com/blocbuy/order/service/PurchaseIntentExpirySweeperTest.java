package com.blocbuy.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.blocbuy.order.repository.PurchaseIntentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PurchaseIntentExpirySweeperTest {
    private static final Instant NOW = Instant.parse("2026-09-30T05:00:00Z");
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();
    private final PurchaseIntentRepository intents = mock(PurchaseIntentRepository.class);
    private final PurchaseIntentExpiryService expiry = mock(PurchaseIntentExpiryService.class);
    private final PurchaseIntentExpirySweeper sweeper =
            new PurchaseIntentExpirySweeper(intents, expiry);

    @Test
    void emptyAndInvalidBatchesDoNotWrite() {
        assertThatThrownBy(() -> sweeper.sweep(0, () -> true))
                .isInstanceOf(IllegalArgumentException.class);
        when(intents.currentTime()).thenReturn(NOW);
        when(intents.dueForExpiry(NOW, 2)).thenReturn(List.of());
        assertThat(sweeper.sweep(2, () -> true))
                .isEqualTo(new PurchaseIntentExpirySweeper.Result(0, 0, 0, 0));
        verifyNoInteractions(expiry);
    }

    @Test
    void failedAndSkippedItemsYieldTheBatchToOtherIntents() {
        when(intents.currentTime()).thenReturn(NOW);
        when(intents.dueForExpiry(NOW, 3)).thenReturn(List.of(first, second, third));
        when(expiry.expireOne(first)).thenThrow(new IllegalStateException("fault"));
        when(expiry.expireOne(second)).thenReturn(PurchaseIntentExpiryService.Outcome.SKIPPED);
        when(expiry.expireOne(third)).thenReturn(PurchaseIntentExpiryService.Outcome.EXPIRED);
        assertThat(sweeper.sweep(3, () -> true))
                .isEqualTo(new PurchaseIntentExpirySweeper.Result(3, 1, 1, 1));
        verify(intents).deferExpiryRetry(first, PurchaseIntentExpirySweeper.RETRY_DELAY);
        verify(intents).deferExpiryRetry(second, PurchaseIntentExpirySweeper.RETRY_DELAY);
        doThrow(new IllegalStateException("backoff unavailable"))
                .when(intents)
                .deferExpiryRetry(first, PurchaseIntentExpirySweeper.RETRY_DELAY);
        assertThatThrownBy(() -> sweeper.sweep(3, () -> true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(first.toString())
                .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
        doReturn(PurchaseIntentExpiryService.Outcome.SKIPPED).when(expiry).expireOne(first);
        assertThatThrownBy(() -> sweeper.sweep(3, () -> true))
                .satisfies(error -> assertThat(error.getSuppressed()).isEmpty());
    }

    @Test
    void paidCandidatesLeaveTheQueueAndAnExclusionFailureGetsFiniteRecovery() {
        when(intents.currentTime()).thenReturn(NOW);
        when(intents.dueForExpiry(NOW, 1)).thenReturn(List.of(first));
        when(expiry.expireOne(first)).thenReturn(PurchaseIntentExpiryService.Outcome.PAID);
        assertThat(sweeper.sweep(1, () -> true))
                .isEqualTo(new PurchaseIntentExpirySweeper.Result(1, 0, 1, 0));
        verify(intents).excludePaidFromExpiry(first);
        verify(intents, never()).deferExpiryRetry(any(), any());
        doThrow(new IllegalStateException("exclusion unavailable"))
                .when(intents)
                .excludePaidFromExpiry(first);
        assertThat(sweeper.sweep(1, () -> true))
                .isEqualTo(new PurchaseIntentExpirySweeper.Result(1, 0, 0, 1));
        verify(intents).deferExpiryRetry(first, PurchaseIntentExpirySweeper.RETRY_DELAY);
    }

    @Test
    void aLostLeaseStopsBeforeTheNextIntent() {
        when(intents.currentTime()).thenReturn(NOW);
        when(intents.dueForExpiry(NOW, 2)).thenReturn(List.of(first, second));
        var valid = new AtomicBoolean(true);
        when(expiry.expireOne(first))
                .thenAnswer(
                        call -> {
                            valid.set(false);
                            return PurchaseIntentExpiryService.Outcome.EXPIRED;
                        });
        assertThat(sweeper.sweep(2, valid::get))
                .isEqualTo(new PurchaseIntentExpirySweeper.Result(2, 1, 0, 0));
        verify(expiry, never()).expireOne(second);
    }
}
