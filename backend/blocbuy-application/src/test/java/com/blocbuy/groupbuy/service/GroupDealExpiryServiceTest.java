package com.blocbuy.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.event.TeamExpired;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** One team, one short transaction: lock, resolve, announce — or lose the race and say nothing. */
class GroupDealExpiryServiceTest {

    private static final UUID DEAL = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final Instant OPENED = Instant.parse("2026-09-22T09:00:00Z");
    private static final Instant DUE = OPENED.plusSeconds(3600);

    private final PaidGroupDealRepository deals = mock(PaidGroupDealRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final GroupDealExpiryService service = new GroupDealExpiryService(deals, events);

    @Test
    void resolvesAnOverdueTeamAndAnnouncesIt() {
        GroupDeal open = deal(DealStatus.OPEN);
        when(deals.lockDeal(DEAL)).thenReturn(Optional.of(open));
        when(deals.currentTime()).thenReturn(DUE);

        assertThat(service.expireOne(DEAL)).isTrue();

        var written = ArgumentCaptor.forClass(GroupDeal.class);
        verify(deals)
                .expire(
                        org.mockito.ArgumentMatchers.eq(open),
                        written.capture(),
                        org.mockito.ArgumentMatchers.eq(DUE));
        assertThat(written.getValue().status()).isEqualTo(DealStatus.EXPIRED);
        verify(events).publishEvent(new TeamExpired(DEAL, PRODUCT, DUE));
    }

    /** The status-guarded UPDATE matched nothing: a webhook or another sweeper got there first. */
    @Test
    void concurrentResolutionIsAnIdempotentReplayNotAFailure() {
        GroupDeal open = deal(DealStatus.OPEN);
        when(deals.lockDeal(DEAL)).thenReturn(Optional.of(open));
        when(deals.currentTime()).thenReturn(DUE);
        doThrow(new BizException(GroupbuyErrorCode.DEAL_CLOSED))
                .when(deals)
                .expire(any(), any(), any());

        assertThat(service.expireOne(DEAL)).isFalse();

        verify(events, never()).publishEvent(any(Object.class));
    }

    /** The snapshot itself is already terminal, so the aggregate refuses before any write. */
    @Test
    void anAlreadyResolvedTeamIsNeverWrittenAgain() {
        when(deals.lockDeal(DEAL)).thenReturn(Optional.of(deal(DealStatus.SUCCEEDED)));
        when(deals.currentTime()).thenReturn(DUE);

        assertThat(service.expireOne(DEAL)).isFalse();

        verify(deals, never()).expire(any(), any(), any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    /** No row to lock: the team was hard-deleted between the due-list read and this call. */
    @Test
    void anAbsentTeamResolvesNothing() {
        when(deals.lockDeal(DEAL)).thenReturn(Optional.empty());

        assertThat(service.expireOne(DEAL)).isFalse();

        verify(deals, never()).currentTime();
        verify(events, never()).publishEvent(any(Object.class));
    }

    /** A team that is not due yet means the caller's own query is wrong; it must not be hidden. */
    @Test
    void aTeamThatIsNotDueYetEscapesAsATechnicalFault() {
        when(deals.lockDeal(DEAL)).thenReturn(Optional.of(deal(DealStatus.OPEN)));
        when(deals.currentTime()).thenReturn(DUE.minusSeconds(1));

        assertThatThrownBy(() -> service.expireOne(DEAL)).isInstanceOf(IllegalStateException.class);

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void rejectsAMissingTeamIdentity() {
        assertThatThrownBy(() -> service.expireOne(null)).isInstanceOf(BizException.class);
    }

    private static GroupDeal deal(DealStatus status) {
        return GroupDeal.reconstitute(
                DEAL,
                "GB-1",
                PRODUCT,
                UUID.fromString("a0000000-0000-7000-8000-000000000003"),
                new BigDecimal("10.00"),
                "sgd",
                3,
                1,
                status,
                OPENED,
                DUE,
                null);
    }
}
