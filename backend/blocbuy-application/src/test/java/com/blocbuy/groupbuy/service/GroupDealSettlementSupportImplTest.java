package com.blocbuy.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.PaidMember;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The locking and reconciliation groupbuy lends to a settlement it does not itself run. */
class GroupDealSettlementSupportImplTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final UUID DEAL = UUID.randomUUID();
    private static final UUID PAYMENT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();

    private final PaidGroupDealRepository deals = mock(PaidGroupDealRepository.class);
    private final GroupDealSettlementSupportImpl support =
            new GroupDealSettlementSupportImpl(deals);

    private static GroupDeal deal(DealStatus status) {
        return GroupDeal.reconstitute(
                DEAL,
                "DEMO-D-0001",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("12.50"),
                "sgd",
                3,
                3,
                status,
                NOW.minusSeconds(3600),
                NOW.minusSeconds(60),
                null);
    }

    @Test
    void lockingATeamTakesTheRowLockAndJudgesNothing() {
        when(deals.lockDeal(DEAL)).thenReturn(Optional.of(deal(DealStatus.SUCCEEDED)));

        support.lockTeam(DEAL);

        verify(deals).lockDeal(DEAL);
    }

    @Test
    void aStartWhoseTeamDoesNotExistYetIsNotAnError() {
        when(deals.lockDeal(DEAL)).thenReturn(Optional.empty());

        support.lockTeam(DEAL);

        verify(deals).lockDeal(DEAL);
    }

    @Test
    void aMissingDealIdIsAProgrammingError() {
        assertThatThrownBy(() -> support.lockTeam(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theOrderLinkPassesTheRepositoryVerdictThrough() {
        when(deals.linkOrder(DEAL, PAYMENT, ORDER)).thenReturn(true, false);

        assertThat(support.linkOrder(DEAL, PAYMENT, ORDER)).isTrue();
        assertThat(support.linkOrder(DEAL, PAYMENT, ORDER)).isFalse();
    }

    @Test
    void theSettledMemberIsHandedOverAsAValueNotAnAggregate() {
        when(deals.findPaidMember(PAYMENT))
                .thenReturn(Optional.of(new PaidMember(DEAL, USER, ORDER, "paid")));

        assertThat(support.settledMember(PAYMENT))
                .contains(new GroupDealSettlementSupport.SettledMember(DEAL, USER, ORDER, "paid"));
    }

    @Test
    void aPaymentThatAdmittedNobodyHasNoMember() {
        when(deals.findPaidMember(PAYMENT)).thenReturn(Optional.empty());

        assertThat(support.settledMember(PAYMENT)).isEmpty();
    }

    @Test
    void anExpiredTeamLocksCleanly() {
        when(deals.lockDeal(DEAL)).thenReturn(Optional.of(deal(DealStatus.EXPIRED)));

        support.lockExpiredTeam(DEAL);

        verify(deals).lockDeal(DEAL);
    }

    @ParameterizedTest
    @EnumSource(value = DealStatus.class, names = "EXPIRED", mode = EnumSource.Mode.EXCLUDE)
    void sweepingRefundsForATeamThatIsNotExpiredIsATechnicalFault(DealStatus status) {
        when(deals.lockDeal(DEAL)).thenReturn(Optional.of(deal(status)));

        assertThatThrownBy(() -> support.lockExpiredTeam(DEAL))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aVanishedTeamCannotBeSweptEither() {
        when(deals.lockDeal(DEAL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> support.lockExpiredTeam(DEAL))
                .isInstanceOf(IllegalStateException.class);
    }
}
