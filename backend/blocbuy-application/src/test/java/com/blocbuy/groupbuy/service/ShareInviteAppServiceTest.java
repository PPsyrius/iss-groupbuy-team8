package com.blocbuy.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.ShareInvite;
import com.blocbuy.groupbuy.query.ShareInviteView;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import com.blocbuy.groupbuy.repository.ShareInviteRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** UC7 minting: members only, open teams only, and one invite per team and member. */
class ShareInviteAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final UUID DEAL = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final String REF = "AAAAAAAAAAAAAAAAAAAAAA";

    private final GroupDealRepository deals = mock(GroupDealRepository.class);
    private final PaidGroupDealRepository participation = mock(PaidGroupDealRepository.class);
    private final ShareInviteRepository invites = mock(ShareInviteRepository.class);
    private final ShareInviteAppService service =
            new ShareInviteAppService(deals, participation, invites);

    private static GroupDeal deal(DealStatus status, Instant expireTime) {
        return GroupDeal.reconstitute(
                DEAL,
                "DEMO-D-0001",
                PRODUCT,
                UUID.randomUUID(),
                new BigDecimal("12.50"),
                "sgd",
                3,
                1,
                status,
                NOW.minusSeconds(600),
                expireTime,
                null);
    }

    private void stored(GroupDeal deal, boolean member) {
        when(deals.findById(DEAL)).thenReturn(Optional.of(deal));
        when(participation.hasActiveMember(DEAL, MEMBER)).thenReturn(member);
        when(participation.currentTime()).thenReturn(NOW);
    }

    private static void assertRejected(ThrowingCallable call, GroupbuyErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(thrown -> ((BizException) thrown).errorCode())
                .isEqualTo(expected);
    }

    @Test
    void aMemberOfAnOpenTeamMintsAnInviteOnFirstUse() {
        Instant expiry = NOW.plusSeconds(3600);
        stored(deal(DealStatus.OPEN, expiry), true);
        var minted = ArgumentCaptor.forClass(ShareInvite.class);
        when(invites.findByDealAndSharer(DEAL, MEMBER))
                .thenReturn(Optional.empty())
                .thenAnswer(call -> Optional.of(minted.getValue()));
        when(invites.insertIfAbsent(minted.capture())).thenReturn(true);

        ShareInviteView view = service.mint(MEMBER, DEAL);

        assertThat(minted.getValue().dealId()).isEqualTo(DEAL);
        assertThat(minted.getValue().sharerUserId()).isEqualTo(MEMBER);
        assertThat(view)
                .isEqualTo(new ShareInviteView(minted.getValue().ref(), DEAL, PRODUCT, expiry));
    }

    @Test
    void sharingAgainReturnsTheSameInviteWithoutMintingAnother() {
        stored(deal(DealStatus.OPEN, NOW.plusSeconds(3600)), true);
        when(invites.findByDealAndSharer(DEAL, MEMBER))
                .thenReturn(Optional.of(new ShareInvite(UUID.randomUUID(), REF, DEAL, MEMBER)));

        assertThat(service.mint(MEMBER, DEAL).ref()).isEqualTo(REF);
        verify(invites, never()).insertIfAbsent(any());
    }

    @Test
    void aConcurrentMintThatWonIsReadBackRatherThanDuplicated() {
        stored(deal(DealStatus.OPEN, NOW.plusSeconds(3600)), true);
        when(invites.findByDealAndSharer(DEAL, MEMBER))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ShareInvite(UUID.randomUUID(), REF, DEAL, MEMBER)));
        when(invites.insertIfAbsent(any())).thenReturn(false);

        assertThat(service.mint(MEMBER, DEAL).ref()).isEqualTo(REF);
    }

    @Test
    void anInsertThatLeavesNoRowIsATechnicalFailure() {
        stored(deal(DealStatus.OPEN, NOW.plusSeconds(3600)), true);
        when(invites.findByDealAndSharer(DEAL, MEMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mint(MEMBER, DEAL))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aNonMemberCannotTellTheTeamExists() {
        stored(deal(DealStatus.OPEN, NOW.plusSeconds(3600)), false);

        assertRejected(() -> service.mint(MEMBER, DEAL), GroupbuyErrorCode.GROUPBUY_PLACEHOLDER);
        verifyNoInteractions(invites);
    }

    @Test
    void aMissingTeamIsNotFound() {
        when(deals.findById(DEAL)).thenReturn(Optional.empty());

        assertRejected(() -> service.mint(MEMBER, DEAL), GroupbuyErrorCode.GROUPBUY_PLACEHOLDER);
    }

    @Test
    void missingIdentifiersAreNotFound() {
        assertRejected(() -> service.mint(null, DEAL), GroupbuyErrorCode.GROUPBUY_PLACEHOLDER);
        assertRejected(() -> service.mint(MEMBER, null), GroupbuyErrorCode.GROUPBUY_PLACEHOLDER);
        verifyNoInteractions(deals, invites);
    }

    @Test
    void aResolvedTeamNoLongerMintsInvites() {
        stored(deal(DealStatus.SUCCEEDED, NOW.plusSeconds(3600)), true);

        assertRejected(() -> service.mint(MEMBER, DEAL), GroupbuyErrorCode.DEAL_CLOSED);
        verifyNoInteractions(invites);
    }

    @Test
    void anOpenTeamPastItsDeadlineNoLongerMintsInvites() {
        stored(deal(DealStatus.OPEN, NOW), true);

        assertRejected(() -> service.mint(MEMBER, DEAL), GroupbuyErrorCode.DEAL_CLOSED);
    }
}
