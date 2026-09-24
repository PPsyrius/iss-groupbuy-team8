package com.blocbuy.groupbuy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Paid deal lifecycle: initiation, admission, terminal states, legacy-row guards. */
class PaidGroupDealTest {
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");
    private static final UUID LEADER = UUID.fromString("a0000000-0000-7000-8000-000000000001");

    /** Verifies that initiation counts only its paid leader and does not prematurely succeed. */
    @Test
    void createsOpenDealWithExactlyOnePaidLeader() {
        GroupDeal deal = start(3);
        assertThat(deal.dealNo()).isEqualTo("TEST-1");
        assertThat(deal.currency()).isEqualTo("sgd");
        assertThat(deal.targetSize()).isEqualTo(3);
        assertThat(deal.currentSize()).isEqualTo(1);
        assertThat(deal.leaderUserId()).isEqualTo(LEADER);
        assertThat(deal.status()).isEqualTo(DealStatus.OPEN);
        assertThat(deal.startTime()).isEqualTo(NOW);
        assertThat(deal.successTime()).isNull();
    }

    /** Verifies reconstructed snapshots reject absent or structurally invalid required fields. */
    @Test
    void reconstituteRejectsMalformedSnapshot() {
        UUID id = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        BigDecimal price = new BigDecimal("10.00");
        Instant expiry = NOW.plusSeconds(60);

        assertMalformed(
                () ->
                        GroupDeal.reconstitute(
                                null,
                                "TEST-1",
                                productId,
                                LEADER,
                                price,
                                "sgd",
                                2,
                                1,
                                DealStatus.OPEN,
                                NOW,
                                expiry,
                                null));
        assertMalformed(
                () ->
                        GroupDeal.reconstitute(
                                id,
                                "TEST-1",
                                null,
                                LEADER,
                                price,
                                "sgd",
                                2,
                                1,
                                DealStatus.OPEN,
                                NOW,
                                expiry,
                                null));
        assertMalformed(
                () ->
                        GroupDeal.reconstitute(
                                id,
                                "TEST-1",
                                productId,
                                null,
                                price,
                                "sgd",
                                2,
                                1,
                                DealStatus.OPEN,
                                NOW,
                                expiry,
                                null));
        assertMalformed(
                () ->
                        GroupDeal.reconstitute(
                                id,
                                "TEST-1",
                                productId,
                                LEADER,
                                null,
                                "sgd",
                                2,
                                1,
                                DealStatus.OPEN,
                                NOW,
                                expiry,
                                null));
        assertMalformed(
                () ->
                        GroupDeal.reconstitute(
                                id,
                                "TEST-1",
                                productId,
                                LEADER,
                                BigDecimal.ONE.negate(),
                                "sgd",
                                2,
                                1,
                                DealStatus.OPEN,
                                NOW,
                                expiry,
                                null));
        assertMalformed(
                () ->
                        GroupDeal.reconstitute(
                                id,
                                "TEST-1",
                                productId,
                                LEADER,
                                price,
                                null,
                                2,
                                1,
                                DealStatus.OPEN,
                                NOW,
                                expiry,
                                null));
        assertMalformed(
                () ->
                        GroupDeal.reconstitute(
                                id,
                                "TEST-1",
                                productId,
                                LEADER,
                                price,
                                " ",
                                2,
                                1,
                                DealStatus.OPEN,
                                NOW,
                                expiry,
                                null));
        assertMalformed(
                () ->
                        GroupDeal.reconstitute(
                                id,
                                "TEST-1",
                                productId,
                                LEADER,
                                price,
                                "sgd",
                                1,
                                1,
                                DealStatus.OPEN,
                                NOW,
                                expiry,
                                null));
        assertMalformed(
                () ->
                        GroupDeal.reconstitute(
                                id, "TEST-1", productId, LEADER, price, "sgd", 2, 1, null, NOW,
                                expiry, null));
        assertMalformed(
                () ->
                        GroupDeal.reconstitute(
                                id,
                                "TEST-1",
                                productId,
                                LEADER,
                                price,
                                "sgd",
                                2,
                                1,
                                DealStatus.OPEN,
                                null,
                                expiry,
                                null));
        assertMalformed(
                () ->
                        GroupDeal.reconstitute(
                                id,
                                "TEST-1",
                                productId,
                                LEADER,
                                price,
                                "sgd",
                                2,
                                1,
                                DealStatus.OPEN,
                                NOW,
                                null,
                                null));
    }

    /**
     * Verifies the capacity-reaching payment stamps success while leaving the prior snapshot
     * unchanged.
     */
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

    /** Verifies initiation rejects absent payment facts and an amount below the accepted price. */
    @Test
    void rejectsMissingPaymentAndIncorrectAmountDuringInitiation() {
        PaidGroupDealCreator creator = new StandardPaidGroupDealCreator();
        NewDealTerms terms = terms(3);

        assertThatThrownBy(() -> creator.initiate(terms, null, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));

        PaidParticipation incorrectPayment = payment(LEADER, "9.99");
        assertThatThrownBy(() -> creator.initiate(terms, incorrectPayment, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.PAYMENT_MISMATCH));
    }

    /** Verifies initiation rejects absent terms without touching payment validation. */
    @Test
    void initiationRejectsNullTerms() {
        PaidGroupDealCreator creator = new StandardPaidGroupDealCreator();
        PaidParticipation payment = payment(LEADER, "10.00");

        assertThatThrownBy(() -> creator.initiate(null, payment, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));
    }

    /** Verifies a null amount is rejected without reaching scale normalization. */
    @Test
    void exactMoneyRejectsNullAmount() {
        assertThatThrownBy(() -> GroupDealMoney.exactSgd(null, "sgd"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));
    }

    /** Verifies neither the leader nor a buyer already in membership can consume another place. */
    @Test
    void rejectsLeaderAndExistingMember() {
        GroupDeal deal = start(3);

        PaidParticipation leaderPayment = payment(LEADER, "10.00");
        assertThatThrownBy(() -> deal.joinPaid(leaderPayment, false, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.ALREADY_MEMBER));

        PaidParticipation existingMemberPayment = payment(UUID.randomUUID(), "10.00");
        assertThatThrownBy(() -> deal.joinPaid(existingMemberPayment, true, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.ALREADY_MEMBER));
    }

    /**
     * Pins admission to the start-inclusive, expiry-exclusive window using the stable closed code.
     */
    @Test
    void rejectsAtDeadlineAndBeforeStart() {
        GroupDeal deal = start(3);

        PaidParticipation deadlinePayment = payment(UUID.randomUUID(), "10.00");
        Instant deadline = deal.expireTime();
        assertThatThrownBy(() -> deal.joinPaid(deadlinePayment, false, deadline))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(GroupbuyErrorCode.DEAL_CLOSED));

        PaidParticipation beforeStartPayment = payment(UUID.randomUUID(), "10.00");
        Instant beforeStart = NOW.minusSeconds(1);
        assertThatThrownBy(() -> deal.joinPaid(beforeStartPayment, false, beforeStart))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(GroupbuyErrorCode.DEAL_CLOSED));
    }

    /** Verifies join price matching and the SGD-only paid-fact boundary. */
    @Test
    void rejectsWrongAmountOrCurrency() {
        GroupDeal deal = start(3);

        PaidParticipation wrongAmountPayment = payment(UUID.randomUUID(), "11.00");
        assertThatThrownBy(() -> deal.joinPaid(wrongAmountPayment, false, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.PAYMENT_MISMATCH));

        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("10.00");
        assertThatThrownBy(() -> new PaidParticipation(paymentId, userId, amount, "usd"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));
    }

    /** Verifies every terminal lifecycle state rejects new paid admission with the closed code. */
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

        PaidParticipation payment = payment(UUID.randomUUID(), "10.00");
        assertThatThrownBy(() -> deal.joinPaid(payment, false, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(GroupbuyErrorCode.DEAL_CLOSED));
    }

    /** Verifies a legacy OPEN row at capacity is rejected even without a terminal status. */
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

        PaidParticipation payment = payment(UUID.randomUUID(), "10.00");
        assertThatThrownBy(() -> deal.joinPaid(payment, false, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(GroupbuyErrorCode.DEAL_FULL));
    }

    /** Verifies malformed legacy progress and missing admission facts fail before mutation. */
    @Test
    void rejectsMissingAdmissionFactsAndZeroMemberLegacyRow() {
        GroupDeal deal = start(3);
        PaidParticipation missingClockPayment = payment(UUID.randomUUID(), "10.00");

        assertThatThrownBy(() -> deal.joinPaid(null, false, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));
        assertThatThrownBy(() -> deal.joinPaid(missingClockPayment, false, null))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));

        GroupDeal zeroMember =
                GroupDeal.reconstitute(
                        UUID.randomUUID(),
                        "legacy",
                        UUID.randomUUID(),
                        LEADER,
                        new BigDecimal("10.00"),
                        "sgd",
                        2,
                        0,
                        DealStatus.OPEN,
                        NOW,
                        NOW.plusSeconds(60),
                        null);
        PaidParticipation zeroMemberPayment = payment(UUID.randomUUID(), "10.00");

        assertThatThrownBy(() -> zeroMember.joinPaid(zeroMemberPayment, false, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(GroupbuyErrorCode.DEAL_FULL));
    }

    /**
     * Verifies an intermediate admission changes only progress, keeping accepted terms and
     * identity.
     */
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

    /**
     * Rejects non-positive, sub-cent and overflowing amounts with the invalid-participation code.
     */
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

    /** Verifies harmless trailing zeros normalize, while absent payment or buyer identity fails. */
    @Test
    void acceptsExactCentsWithoutRoundingAndRejectsMissingIdentity() {
        assertThat(payment(LEADER, "10.000").amount()).isEqualTo(new BigDecimal("10.00"));

        assertThatThrownBy(() -> payment(null, "10.00"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));

        BigDecimal amount = new BigDecimal("10.00");
        assertThatThrownBy(() -> new PaidParticipation(null, LEADER, amount, "sgd"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));
    }

    /**
     * Verifies initiation needs an unexpired deadline, at least two places and an explicit clock.
     */
    @Test
    void initiationRejectsExpiredTermsInvalidSizeAndMissingClock() {
        PaidGroupDealCreator creator = new StandardPaidGroupDealCreator();
        NewDealTerms terms = terms(3);
        PaidParticipation expiredTermsPayment = payment(LEADER, "10.00");
        Instant expireTime = terms.expireTime();

        assertThatThrownBy(() -> creator.initiate(terms, expiredTermsPayment, expireTime))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));

        PaidParticipation missingClockPayment = payment(LEADER, "10.00");
        assertThatThrownBy(() -> creator.initiate(terms, missingClockPayment, null))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));

        assertThatThrownBy(() -> terms(1))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION));
    }

    /** Verifies an SGD payment cannot enter a legacy USD deal and preserves the mismatch code. */
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

        PaidParticipation payment = payment(UUID.randomUUID(), "10.00");
        assertThatThrownBy(() -> deal.joinPaid(payment, false, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(GroupbuyErrorCode.PAYMENT_MISMATCH));
    }

    /** Verifies an overdue open deal resolves to an expired snapshot, changing nothing else. */
    @Test
    void expireClosesAnOverdueOpenDeal() {
        GroupDeal deal = start(3);
        Instant due = deal.expireTime();

        GroupDeal expired = deal.expire(due);

        assertThat(expired.status()).isEqualTo(DealStatus.EXPIRED);
        assertThat(expired.id()).isEqualTo(deal.id());
        assertThat(expired.currentSize()).isEqualTo(deal.currentSize());
        assertThat(expired.expireTime()).isEqualTo(deal.expireTime());
        assertThat(expired.successTime()).isNull();
    }

    /**
     * A sweeper asking for a deal that is not due yet is a bug in its own query, not a rejection.
     */
    @Test
    void expireRefusesADealThatIsNotDueYet() {
        GroupDeal deal = start(3);
        Instant early = deal.expireTime().minusMillis(1);

        assertThatThrownBy(() -> deal.expire(early)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * A team another writer already resolved is a business fact the caller swallows as a replay.
     */
    @ParameterizedTest
    @EnumSource(
            value = DealStatus.class,
            names = {"SUCCEEDED", "EXPIRED", "FAILED", "CANCELED"})
    void expireRefusesATerminalDeal(DealStatus terminal) {
        GroupDeal deal = reconstituteWith(terminal);
        Instant due = deal.expireTime();

        assertThatThrownBy(() -> deal.expire(due))
                .isInstanceOf(BizException.class)
                .satisfies(
                        failure ->
                                assertThat(((BizException) failure).errorCode())
                                        .isEqualTo(GroupbuyErrorCode.DEAL_CLOSED));
    }

    @Test
    void expireRequiresAClock() {
        GroupDeal deal = start(3);
        assertMalformed(() -> deal.expire(null));
    }

    /** Rebuilds a stored snapshot in an arbitrary lifecycle state, as the converter would. */
    private static GroupDeal reconstituteWith(DealStatus status) {
        return GroupDeal.reconstitute(
                UUID.randomUUID(),
                "TEST-1",
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
    }

    /** Creates an OPEN domain fixture with one paid leader and the requested target size. */
    private static GroupDeal start(int size) {
        PaidGroupDealCreator creator = new StandardPaidGroupDealCreator();
        return creator.initiate(terms(size), payment(LEADER, "10.00"), NOW);
    }

    /** Verifies accepted nanosecond deadlines normalize to the persistence precision before use. */
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

    /** Supplies distinct accepted deal terms at ten SGD with a fixed sixty-second window. */
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

    /**
     * Builds provider-free payment facts for a chosen buyer and amount, with a fresh payment key.
     */
    private static PaidParticipation payment(UUID userId, String amount) {
        return new PaidParticipation(UUID.randomUUID(), userId, new BigDecimal(amount), "sgd");
    }

    private static void assertMalformed(ThrowingCallable construction) {
        assertThatThrownBy(construction).isInstanceOf(IllegalArgumentException.class);
    }
}
