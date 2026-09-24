package com.blocbuy.order.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Purchase intent invariants: the server's price commitment, its deadlines and its single use. */
class PurchaseIntentTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID DEAL = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();

    private static PurchaseIntent issueWith(
            UUID user, PurchaseOperation operation, UUID dealId, UUID productId) {
        return PurchaseIntent.issue(
                new PurchaseIntent.Terms(
                        user,
                        operation,
                        dealId,
                        productId,
                        "Sourdough starter kit",
                        null,
                        new BigDecimal("12.50"),
                        "sgd",
                        3),
                NOW.plusSeconds(2400),
                NOW.plusSeconds(86_400),
                NOW);
    }

    private static PurchaseIntent issueWithImage(String image) {
        return PurchaseIntent.issue(
                new PurchaseIntent.Terms(
                        USER,
                        PurchaseOperation.JOIN,
                        DEAL,
                        PRODUCT,
                        "Sourdough starter kit",
                        image,
                        new BigDecimal("12.50"),
                        "sgd",
                        3),
                NOW.plusSeconds(2400),
                NOW.plusSeconds(86_400),
                NOW);
    }

    private static PurchaseIntent.Terms terms() {
        return new PurchaseIntent.Terms(
                USER,
                PurchaseOperation.JOIN,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Sourdough starter kit",
                null,
                new BigDecimal("12.50"),
                "sgd",
                3);
    }

    private static PurchaseIntent issued() {
        return PurchaseIntent.issue(terms(), NOW.plusSeconds(2400), NOW.plusSeconds(86_400), NOW);
    }

    @ParameterizedTest
    @EnumSource(PurchaseOperation.class)
    void operationCodesRoundTrip(PurchaseOperation operation) {
        assertThat(PurchaseOperation.fromCode(operation.code())).isEqualTo(operation);
    }

    @Test
    void unknownOperationCodeIsRejected() {
        assertThatThrownBy(() -> PurchaseOperation.fromCode("BUY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsingRequestInputAnswersEmptyInsteadOfThrowing() {
        // Callers that receive the operation from a browser turn the miss into their own typed
        // business rejection, so an unknown value must not arrive as an IllegalArgumentException.
        assertThat(PurchaseOperation.parse("JOIN")).contains(PurchaseOperation.JOIN);
        assertThat(PurchaseOperation.parse("join")).isEmpty();
        assertThat(PurchaseOperation.parse(null)).isEmpty();
    }

    @Test
    void issuedIntentIsUnfulfilledAndKeepsItsTerms() {
        PurchaseIntent intent = issued();
        assertThat(intent.fulfilled()).isFalse();
        assertThat(intent.fulfilledPaymentId()).isNull();
        assertThat(intent.version()).isZero();
        assertThat(intent.amount()).isEqualByComparingTo("12.50");
        assertThat(intent.targetSize()).isEqualTo(3);
        assertThat(intent.operation()).isEqualTo(PurchaseOperation.JOIN);
    }

    @Test
    void deadlinesAreTruncatedToTheMicrosecondPostgresStores() {
        Instant nanos = NOW.plusNanos(1_234_567);
        PurchaseIntent intent = PurchaseIntent.issue(terms(), nanos, nanos.plusSeconds(60), NOW);
        assertThat(intent.quoteValidUntil()).isEqualTo(nanos.truncatedTo(ChronoUnit.MICROS));
        assertThat(intent.businessDeadline())
                .isEqualTo(nanos.plusSeconds(60).truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void aQuoteThatIsAlreadyPastIsNotIssued() {
        PurchaseIntent.Terms terms = terms();
        Instant businessDeadline = NOW.plusSeconds(86_400);

        assertThatThrownBy(() -> PurchaseIntent.issue(terms, NOW, businessDeadline, NOW))
                .isInstanceOf(BizException.class);
    }

    @Test
    void aDeadlineBeforeTheQuoteIsNotIssued() {
        PurchaseIntent.Terms terms = terms();
        Instant quoteValidUntil = NOW.plusSeconds(2400);
        Instant businessDeadline = NOW.plusSeconds(600);

        assertThatThrownBy(
                        () -> PurchaseIntent.issue(terms, quoteValidUntil, businessDeadline, NOW))
                .isInstanceOf(BizException.class);
    }

    @Test
    void expiryIsExclusiveOnBothDeadlines() {
        PurchaseIntent intent = issued();
        assertThat(intent.expiredAt(NOW.plusSeconds(2399))).isFalse();
        assertThat(intent.expiredAt(NOW.plusSeconds(2400))).isTrue();

        // A team deadline can never precede its own quote, so the two coincide at the earliest.
        PurchaseIntent shortTeam =
                PurchaseIntent.issue(terms(), NOW.plusSeconds(600), NOW.plusSeconds(600), NOW);
        assertThat(shortTeam.expiredAt(NOW.plusSeconds(599))).isFalse();
        assertThat(shortTeam.expiredAt(NOW.plusSeconds(600))).isTrue();
    }

    @Test
    void ownershipMismatchReadsAsNotFound() {
        PurchaseIntent intent = issued();
        UUID anotherUser = UUID.randomUUID();

        assertThatThrownBy(() -> intent.requireOwner(anotherUser)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> intent.requireOwner(null)).isInstanceOf(BizException.class);
        intent.requireOwner(USER);
    }

    @Test
    void oneIntentIsConsumedByOnePayment() {
        UUID payment = UUID.randomUUID();
        PurchaseIntent fulfilled = issued().fulfilBy(payment);
        assertThat(fulfilled.fulfilled()).isTrue();
        assertThat(fulfilled.fulfilledPaymentId()).isEqualTo(payment);

        assertThat(fulfilled.fulfilBy(payment)).isSameAs(fulfilled);
        UUID anotherPayment = UUID.randomUUID();
        assertThatThrownBy(() -> fulfilled.fulfilBy(anotherPayment))
                .isInstanceOf(BizException.class);
    }

    @Test
    void fulfilmentNeedsAPayment() {
        PurchaseIntent intent = issued();

        assertThatThrownBy(() -> intent.fulfilBy(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void aTeamOfFewerThanTwoIsNotAPurchaseIntent(int targetSize) {
        PurchaseIntent.Terms small =
                new PurchaseIntent.Terms(
                        USER,
                        PurchaseOperation.START,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Sourdough starter kit",
                        null,
                        new BigDecimal("12.50"),
                        "sgd",
                        targetSize);
        Instant quoteValidUntil = NOW.plusSeconds(2400);
        Instant businessDeadline = NOW.plusSeconds(86_400);
        assertThatThrownBy(
                        () -> PurchaseIntent.issue(small, quoteValidUntil, businessDeadline, NOW))
                .isInstanceOf(BizException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "SGD", ""})
    void onlySgdIsQuoted(String currency) {
        BigDecimal amount = new BigDecimal("12.50");

        assertThatThrownBy(() -> issue(amount, currency)).isInstanceOf(BizException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00", "12.505"})
    void anAmountThatIsNotPositiveExactCentsIsRejected(String amount) {
        BigDecimal parsedAmount = new BigDecimal(amount);

        assertThatThrownBy(() -> issue(parsedAmount, "sgd")).isInstanceOf(BizException.class);
    }

    @Test
    void aMissingProductSnapshotIsRejected() {
        String overlongTitle = "x".repeat(257);

        assertThatThrownBy(() -> issueWithTitle(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> issueWithTitle("  ")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> issueWithTitle(overlongTitle)).isInstanceOf(BizException.class);
    }

    @Test
    void everyMissingPieceOfTheTermsIsRejectedOnItsOwn() {
        Instant quoteValidUntil = NOW.plusSeconds(60);

        assertThatThrownBy(() -> PurchaseIntent.issue(null, quoteValidUntil, NOW, NOW))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> issueWith(null, PurchaseOperation.JOIN, DEAL, PRODUCT))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> issueWith(USER, null, DEAL, PRODUCT))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> issueWith(USER, PurchaseOperation.JOIN, null, PRODUCT))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> issueWith(USER, PurchaseOperation.JOIN, DEAL, null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void anOverlongProductImageIsRejectedButAShortOneIsKept() {
        String overlongImage = "https://cdn/" + "x".repeat(1024);

        assertThatThrownBy(() -> issueWithImage(overlongImage)).isInstanceOf(BizException.class);
        assertThat(issueWithImage("https://cdn/cover.jpg").productImage())
                .isEqualTo("https://cdn/cover.jpg");
    }

    @Test
    void anAmountWiderThanTheColumnIsRejected() {
        BigDecimal overlongAmount = new BigDecimal("99999999999999999.00");

        assertThatThrownBy(() -> issue(overlongAmount, "sgd")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> issue(null, "sgd")).isInstanceOf(BizException.class);
    }

    @Test
    void issuingNeedsAClockAndAQuoteDeadline() {
        PurchaseIntent.Terms terms = terms();
        Instant quoteValidUntil = NOW.plusSeconds(2400);
        Instant businessDeadline = NOW.plusSeconds(86_400);

        assertThatThrownBy(
                        () -> PurchaseIntent.issue(terms, quoteValidUntil, businessDeadline, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PurchaseIntent.issue(terms, null, businessDeadline, NOW))
                .isInstanceOf(BizException.class);
    }

    @Test
    void aReconstitutedRowWithoutATeamDeadlineIsImpossible() {
        UUID id = UUID.randomUUID();
        PurchaseIntent.Terms terms = terms();
        Instant quoteValidUntil = NOW.plusSeconds(2400);

        assertThatThrownBy(
                        () ->
                                PurchaseIntent.reconstitute(
                                        id, terms, quoteValidUntil, null, null, NOW, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTermsRecordIsReadableAsAWhole() {
        assertThat(issued().terms().productTitle()).isEqualTo("Sourdough starter kit");
    }

    @Test
    void aReconstitutedIntentCarriesItsStoredVersionAndPayment() {
        UUID id = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        PurchaseIntent stored =
                PurchaseIntent.reconstitute(
                        id,
                        terms(),
                        NOW.plusSeconds(2400),
                        NOW.plusSeconds(86_400),
                        payment,
                        NOW,
                        4);
        assertThat(stored.id()).isEqualTo(id);
        assertThat(stored.version()).isEqualTo(4);
        assertThat(stored.createTime()).isEqualTo(NOW);
        assertThat(stored.fulfilledPaymentId()).isEqualTo(payment);
    }

    @Test
    void reconstitutionRefusesAnImpossibleRow() {
        PurchaseIntent.Terms terms = terms();
        Instant quoteValidUntil = NOW.plusSeconds(2400);
        Instant businessDeadline = NOW.plusSeconds(86_400);
        UUID invalidVersionId = UUID.randomUUID();
        UUID missingQuoteId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                PurchaseIntent.reconstitute(
                                        null,
                                        terms,
                                        quoteValidUntil,
                                        businessDeadline,
                                        null,
                                        NOW,
                                        0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                PurchaseIntent.reconstitute(
                                        invalidVersionId,
                                        terms,
                                        quoteValidUntil,
                                        businessDeadline,
                                        null,
                                        NOW,
                                        -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                PurchaseIntent.reconstitute(
                                        missingQuoteId,
                                        terms,
                                        null,
                                        businessDeadline,
                                        null,
                                        NOW,
                                        0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PurchaseIntent issue(BigDecimal amount, String currency) {
        return PurchaseIntent.issue(
                new PurchaseIntent.Terms(
                        USER,
                        PurchaseOperation.JOIN,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Sourdough starter kit",
                        null,
                        amount,
                        currency,
                        3),
                NOW.plusSeconds(2400),
                NOW.plusSeconds(86_400),
                NOW);
    }

    private static PurchaseIntent issueWithTitle(String title) {
        return PurchaseIntent.issue(
                new PurchaseIntent.Terms(
                        USER,
                        PurchaseOperation.JOIN,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        title,
                        null,
                        new BigDecimal("12.50"),
                        "sgd",
                        3),
                NOW.plusSeconds(2400),
                NOW.plusSeconds(86_400),
                NOW);
    }
}
