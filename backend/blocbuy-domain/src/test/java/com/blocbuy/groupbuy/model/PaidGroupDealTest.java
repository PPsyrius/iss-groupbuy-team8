package com.blocbuy.groupbuy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class PaidGroupDealTest {
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");
    private static final UUID LEADER = UUID.fromString("a0000000-0000-7000-8000-000000000001");

    @Test
    void createsOpenDealWithExactlyOnePaidLeader() {
        GroupDeal deal = start(3);
        assertThat(deal.currentSize()).isEqualTo(1);
        assertThat(deal.leaderUserId()).isEqualTo(LEADER);
        assertThat(deal.status()).isEqualTo(DealStatus.OPEN);
        assertThat(deal.successTime()).isNull();
    }

    @Test
    void lastPaidMemberSucceedsWithoutMutatingOriginalSnapshot() {
        GroupDeal original = start(2);
        GroupDeal joined =
                original.joinPaid(payment(UUID.randomUUID(), "10.00"), false, NOW.plusSeconds(1));
        assertThat(joined.currentSize()).isEqualTo(2);
        assertThat(joined.status()).isEqualTo(DealStatus.SUCCEEDED);
        assertThat(joined.successTime()).isEqualTo(NOW.plusSeconds(1));
        assertThat(original.currentSize()).isEqualTo(1);
        assertThat(original.status()).isEqualTo(DealStatus.OPEN);
    }

    @Test
    void rejectsMissingPaymentAndIncorrectAmountDuringInitiation() {
        assertThatThrownBy(() -> new StandardPaidGroupDealCreator().initiate(terms(3), null, NOW))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(
                        () ->
                                new StandardPaidGroupDealCreator()
                                        .initiate(terms(3), payment(LEADER, "9.99"), NOW))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rejectsLeaderAndExistingMember() {
        GroupDeal deal = start(3);
        assertThatThrownBy(() -> deal.joinPaid(payment(LEADER, "10.00"), false, NOW))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> deal.joinPaid(payment(UUID.randomUUID(), "10.00"), true, NOW))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rejectsAtDeadlineAndBeforeStart() {
        GroupDeal deal = start(3);
        assertThatThrownBy(
                        () ->
                                deal.joinPaid(
                                        payment(UUID.randomUUID(), "10.00"),
                                        false,
                                        deal.expireTime()))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(GroupbuyErrorCode.DEAL_CLOSED));
        assertThatThrownBy(
                        () ->
                                deal.joinPaid(
                                        payment(UUID.randomUUID(), "10.00"),
                                        false,
                                        NOW.minusSeconds(1)))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(GroupbuyErrorCode.DEAL_CLOSED));
    }

    @Test
    void rejectsWrongAmountOrCurrency() {
        GroupDeal deal = start(3);
        assertThatThrownBy(() -> deal.joinPaid(payment(UUID.randomUUID(), "11.00"), false, NOW))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(
                        () ->
                                new PaidParticipation(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        new BigDecimal("10.00"),
                                        "usd"))
                .isInstanceOf(BizException.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = DealStatus.class,
            names = {"SUCCEEDED", "FAILED", "EXPIRED", "CANCELED"})
    void terminalStatesRejectJoins(DealStatus status) {
        GroupDeal deal =
                GroupDeal.reconstitute(
                        UUID.randomUUID(),
                        "test",
                        UUID.randomUUID(),
                        LEADER,
                        new BigDecimal("10.00"),
                        "sgd",
                        3,
                        1,
                        status,
                        NOW,
                        NOW.plusSeconds(60),
                        null);
        assertThatThrownBy(() -> deal.joinPaid(payment(UUID.randomUUID(), "10.00"), false, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(GroupbuyErrorCode.DEAL_CLOSED));
    }

    @Test
    void rejectsFullOpenLegacyRow() {
        GroupDeal deal =
                GroupDeal.reconstitute(
                        UUID.randomUUID(),
                        "test",
                        UUID.randomUUID(),
                        LEADER,
                        new BigDecimal("10.00"),
                        "sgd",
                        2,
                        2,
                        DealStatus.OPEN,
                        NOW,
                        NOW.plusSeconds(60),
                        null);
        assertThatThrownBy(() -> deal.joinPaid(payment(UUID.randomUUID(), "10.00"), false, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(GroupbuyErrorCode.DEAL_FULL));
    }

    @Test
    void middleMemberPreservesIdentityAndFrozenTerms() {
        GroupDeal original = start(3);
        GroupDeal joined = original.joinPaid(payment(UUID.randomUUID(), "10.0"), false, NOW);
        assertThat(joined.currentSize()).isEqualTo(2);
        assertThat(joined.status()).isEqualTo(DealStatus.OPEN);
        assertThat(joined.successTime()).isNull();
        assertThat(joined.id()).isEqualTo(original.id());
        assertThat(joined.productId()).isEqualTo(original.productId());
        assertThat(joined.leaderUserId()).isEqualTo(original.leaderUserId());
        assertThat(joined.dealPrice()).isEqualTo(original.dealPrice());
        assertThat(joined.expireTime()).isEqualTo(original.expireTime());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "10000000000000000.00"})
    void rejectsUnrepresentablePaymentAmounts(String amount) {
        assertThatThrownBy(() -> payment(LEADER, amount))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));
    }

    @Test
    void acceptsExactCentsWithoutRoundingAndRejectsMissingIdentity() {
        assertThat(payment(LEADER, "10.000").amount()).isEqualTo(new BigDecimal("10.00"));
        assertThatThrownBy(() -> payment(null, "10.00")).isInstanceOf(BizException.class);
        assertThatThrownBy(
                        () -> new PaidParticipation(null, LEADER, new BigDecimal("10.00"), "sgd"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void initiationRejectsExpiredTermsInvalidSizeAndMissingClock() {
        PaidGroupDealCreator creator = new StandardPaidGroupDealCreator();
        NewDealTerms terms = terms(3);
        assertThatThrownBy(
                        () -> creator.initiate(terms, payment(LEADER, "10.00"), terms.expireTime()))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> creator.initiate(terms, payment(LEADER, "10.00"), null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> terms(1)).isInstanceOf(BizException.class);
    }

    @Test
    void joinRejectsLegacyCurrencyMismatchWithStableErrorCode() {
        GroupDeal deal =
                GroupDeal.reconstitute(
                        UUID.randomUUID(),
                        "USD",
                        UUID.randomUUID(),
                        LEADER,
                        new BigDecimal("10.00"),
                        "usd",
                        3,
                        1,
                        DealStatus.OPEN,
                        NOW,
                        NOW.plusSeconds(60),
                        null);
        assertThatThrownBy(() -> deal.joinPaid(payment(UUID.randomUUID(), "10.00"), false, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.PAYMENT_MISMATCH));
    }

    private static GroupDeal start(int size) {
        PaidGroupDealCreator creator = new StandardPaidGroupDealCreator();
        return creator.initiate(terms(size), payment(LEADER, "10.00"), NOW);
    }

    @Test
    void acceptedDeadlineMatchesPostgresMicrosecondPrecision() {
        NewDealTerms terms =
                new NewDealTerms(
                        UUID.randomUUID(),
                        "PRECISION",
                        UUID.randomUUID(),
                        new BigDecimal("10.00"),
                        "sgd",
                        2,
                        NOW.plusSeconds(60).plusNanos(999));
        assertThat(terms.expireTime()).isEqualTo(NOW.plusSeconds(60));
    }

    private static NewDealTerms terms(int size) {
        return new NewDealTerms(
                UUID.randomUUID(),
                "TEST-1",
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                "sgd",
                size,
                NOW.plusSeconds(60));
    }

    private static PaidParticipation payment(UUID userId, String amount) {
        return new PaidParticipation(UUID.randomUUID(), userId, new BigDecimal(amount), "sgd");
    }
}
