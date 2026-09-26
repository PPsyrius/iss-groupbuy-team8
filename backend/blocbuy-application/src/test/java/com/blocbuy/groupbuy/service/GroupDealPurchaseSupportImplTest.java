package com.blocbuy.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.ShareInvite;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import com.blocbuy.groupbuy.repository.ShareInviteRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Which teams groupbuy will let an unpaid checkout be quoted against. */
class GroupDealPurchaseSupportImplTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final UUID DEAL = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID LEADER = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID SHARER = UUID.randomUUID();
    private static final String REF = "AAAAAAAAAAAAAAAAAAAAAA";

    private final PaidGroupDealRepository deals = mock(PaidGroupDealRepository.class);
    private final ShareInviteRepository invites = mock(ShareInviteRepository.class);
    private final GroupDealPurchaseSupportImpl support =
            new GroupDealPurchaseSupportImpl(deals, invites);

    private static GroupDeal deal(
            DealStatus status, int currentSize, Instant startTime, Instant expireTime) {
        return GroupDeal.reconstitute(
                DEAL,
                "DEMO-D-0001",
                PRODUCT,
                LEADER,
                new BigDecimal("12.50"),
                "sgd",
                3,
                currentSize,
                status,
                startTime,
                expireTime,
                null);
    }

    private static GroupDeal open() {
        return deal(DealStatus.OPEN, 1, NOW.minusSeconds(600), NOW.plusSeconds(3600));
    }

    private void stored(GroupDeal deal) {
        when(deals.lockDeal(DEAL)).thenReturn(Optional.of(deal));
        when(deals.currentTime()).thenReturn(NOW);
    }

    private static void assertRejected(ThrowingCallable call, GroupbuyErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(thrown -> ((BizException) thrown).errorCode())
                .isEqualTo(expected);
    }

    @Test
    void anOpenTeamWithRoomYieldsItsFrozenTerms() {
        stored(open());
        when(deals.hasActiveMember(DEAL, BUYER)).thenReturn(false);

        GroupDealPurchaseSupport.JoinTerms terms = support.lockJoinTerms(DEAL, BUYER);

        assertThat(terms.dealId()).isEqualTo(DEAL);
        assertThat(terms.productId()).isEqualTo(PRODUCT);
        assertThat(terms.amount()).isEqualByComparingTo("12.50");
        assertThat(terms.currency()).isEqualTo("sgd");
        assertThat(terms.targetSize()).isEqualTo(3);
        assertThat(terms.expireTime()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void aMissingDealIsNotFound() {
        when(deals.lockDeal(DEAL)).thenReturn(Optional.empty());

        assertRejected(
                () -> support.lockJoinTerms(DEAL, BUYER), GroupbuyErrorCode.GROUPBUY_PLACEHOLDER);
    }

    @ParameterizedTest
    @EnumSource(value = DealStatus.class, names = "OPEN", mode = EnumSource.Mode.EXCLUDE)
    void onlyAnOpenTeamCanBeQuoted(DealStatus status) {
        stored(deal(status, 1, NOW.minusSeconds(600), NOW.plusSeconds(3600)));

        assertRejected(() -> support.lockJoinTerms(DEAL, BUYER), GroupbuyErrorCode.DEAL_CLOSED);
    }

    @Test
    void aTeamWhoseWindowHasNotOpenedYetIsClosed() {
        stored(deal(DealStatus.OPEN, 1, NOW.plusSeconds(60), NOW.plusSeconds(3600)));

        assertRejected(() -> support.lockJoinTerms(DEAL, BUYER), GroupbuyErrorCode.DEAL_CLOSED);
    }

    @Test
    void expiryIsExclusiveSoTheDeadlineInstantIsAlreadyTooLate() {
        stored(deal(DealStatus.OPEN, 1, NOW.minusSeconds(600), NOW));

        assertRejected(() -> support.lockJoinTerms(DEAL, BUYER), GroupbuyErrorCode.DEAL_CLOSED);
    }

    @Test
    void aTeamThatAlreadyHasEveryMemberIsFull() {
        stored(deal(DealStatus.OPEN, 3, NOW.minusSeconds(600), NOW.plusSeconds(3600)));

        assertRejected(() -> support.lockJoinTerms(DEAL, BUYER), GroupbuyErrorCode.DEAL_FULL);
    }

    @Test
    void theLeaderCannotJoinTheirOwnTeamAgain() {
        stored(open());

        assertRejected(() -> support.lockJoinTerms(DEAL, LEADER), GroupbuyErrorCode.ALREADY_MEMBER);
    }

    @Test
    void anExistingMemberCannotQuoteASecondSeat() {
        stored(open());
        when(deals.hasActiveMember(DEAL, BUYER)).thenReturn(true);

        assertRejected(() -> support.lockJoinTerms(DEAL, BUYER), GroupbuyErrorCode.ALREADY_MEMBER);
    }

    @Test
    void missingIdentifiersAreRejectedBeforeAnyLockIsTaken() {
        assertRejected(
                () -> support.lockJoinTerms(null, BUYER), GroupbuyErrorCode.INVALID_PARTICIPATION);
        assertRejected(
                () -> support.lockJoinTerms(DEAL, null), GroupbuyErrorCode.INVALID_PARTICIPATION);
        verifyNoInteractions(deals);
    }

    @Test
    void theClockIsReadOnlyAfterTheLock() {
        // Guards the ordering that makes the window check trustworthy: a clock read before the row
        // lock could predate a concurrent writer's commit.
        stored(open());
        when(deals.hasActiveMember(DEAL, BUYER)).thenReturn(false);

        support.lockJoinTerms(DEAL, BUYER);

        var order = inOrder(deals);
        order.verify(deals).lockDeal(DEAL);
        order.verify(deals).currentTime();
    }

    @Test
    void anInviteIntoThisTeamFromAnotherMemberResolvesToItsSharer() {
        when(invites.findByRef(REF))
                .thenReturn(Optional.of(new ShareInvite(UUID.randomUUID(), REF, DEAL, SHARER)));

        assertThat(support.resolveSharer(REF, DEAL, BUYER)).contains(SHARER);
    }

    @Test
    void anAbsentReferenceAttributesNothingWithoutALookup() {
        assertThat(support.resolveSharer(null, DEAL, BUYER)).isEmpty();
        assertThat(support.resolveSharer(" ", DEAL, BUYER)).isEmpty();
        verifyNoInteractions(invites);
    }

    @Test
    void anUnknownReferenceAttributesNothing() {
        when(invites.findByRef(REF)).thenReturn(Optional.empty());

        assertThat(support.resolveSharer(REF, DEAL, BUYER)).isEmpty();
    }

    @Test
    void anInviteIntoAnotherTeamAttributesNothing() {
        when(invites.findByRef(REF))
                .thenReturn(
                        Optional.of(
                                new ShareInvite(
                                        UUID.randomUUID(), REF, UUID.randomUUID(), SHARER)));

        assertThat(support.resolveSharer(REF, DEAL, BUYER)).isEmpty();
    }

    @Test
    void nobodyIsAttributedToTheirOwnInvite() {
        when(invites.findByRef(REF))
                .thenReturn(Optional.of(new ShareInvite(UUID.randomUUID(), REF, DEAL, BUYER)));

        assertThat(support.resolveSharer(REF, DEAL, BUYER)).isEmpty();
    }
}
