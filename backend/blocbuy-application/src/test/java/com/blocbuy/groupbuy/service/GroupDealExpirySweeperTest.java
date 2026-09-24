package com.blocbuy.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** One batch read, one transaction per team, and no team's failure reaches another. */
class GroupDealExpirySweeperTest {

    private static final Instant DB_NOW = Instant.parse("2026-09-23T08:00:00Z");
    private static final UUID FIRST = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final UUID THIRD = UUID.fromString("a0000000-0000-7000-8000-000000000003");

    private final PaidGroupDealRepository deals = mock(PaidGroupDealRepository.class);
    private final GroupDealExpiryService expiry = mock(GroupDealExpiryService.class);
    private final GroupDealExpirySweeper sweeper = new GroupDealExpirySweeper(deals, expiry);

    @Test
    void nothingDueMeansNothingExpired() {
        when(deals.currentTime()).thenReturn(DB_NOW);
        when(deals.dueForExpiry(DB_NOW, 100)).thenReturn(List.of());

        assertThat(sweeper.sweep(100)).isEqualTo(new GroupDealExpirySweeper.Result(0, 0, 0, 0));

        verify(expiry, never()).expireOne(any());
    }

    @Test
    void selectsOnTheDatabaseClockAndCountsReplaysApartFromExpiries() {
        when(deals.currentTime()).thenReturn(DB_NOW);
        when(deals.dueForExpiry(DB_NOW, 2)).thenReturn(List.of(FIRST, SECOND));
        when(expiry.expireOne(FIRST)).thenReturn(true);
        when(expiry.expireOne(SECOND)).thenReturn(false);

        assertThat(sweeper.sweep(2)).isEqualTo(new GroupDealExpirySweeper.Result(2, 1, 1, 0));
    }

    @Test
    void aFailingTeamIsCountedAndTheOthersAreStillResolved() {
        when(deals.currentTime()).thenReturn(DB_NOW);
        when(deals.dueForExpiry(DB_NOW, 100)).thenReturn(List.of(FIRST, SECOND, THIRD));
        when(expiry.expireOne(FIRST)).thenReturn(true);
        when(expiry.expireOne(SECOND)).thenThrow(new IllegalStateException("inconsistent rows"));
        when(expiry.expireOne(THIRD)).thenReturn(true);

        assertThat(sweeper.sweep(100)).isEqualTo(new GroupDealExpirySweeper.Result(3, 2, 0, 1));

        verify(expiry).expireOne(THIRD);
    }

    @Test
    void aNonPositiveLimitIsRejectedBeforeAnyQuery() {
        assertThatThrownBy(() -> sweeper.sweep(0)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(deals, expiry);
    }
}
