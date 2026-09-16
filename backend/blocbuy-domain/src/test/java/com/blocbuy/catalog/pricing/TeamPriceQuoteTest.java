package com.blocbuy.catalog.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Quote snapshot validation: exact money, sgd-only, positive version, ordered validity window. */
class TeamPriceQuoteTest {
    private static final UUID ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("b0000000-0000-7000-8000-000000000001");
    private static final UUID OWNER = UUID.fromString("c0000000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

    @Test
    void happySnapshotPreservesAllFields() {
        TeamPriceQuote quote =
                new TeamPriceQuote(
                        ID,
                        PRODUCT,
                        OWNER,
                        2,
                        new BigDecimal("20.00"),
                        "sgd",
                        NOW,
                        NOW.plusSeconds(900));

        assertThat(quote.id()).isEqualTo(ID);
        assertThat(quote.productId()).isEqualTo(PRODUCT);
        assertThat(quote.ownerId()).isEqualTo(OWNER);
        assertThat(quote.policyVersion()).isEqualTo(2);
        assertThat(quote.amount()).isEqualByComparingTo("20.00");
        assertThat(quote.currency()).isEqualTo("sgd");
        assertThat(quote.issuedAt()).isEqualTo(NOW);
        assertThat(quote.expiresAt()).isEqualTo(NOW.plusSeconds(900));
    }

    @Test
    void rejectsNullIdentityAndWindow() {
        BigDecimal amount = new BigDecimal("20.00");
        Instant expiresAt = NOW.plusSeconds(900);

        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        null, PRODUCT, OWNER, 1, amount, "sgd", NOW, expiresAt))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new TeamPriceQuote(ID, null, OWNER, 1, amount, "sgd", NOW, expiresAt))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID, PRODUCT, null, 1, amount, "sgd", NOW, expiresAt))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID, PRODUCT, OWNER, 1, amount, "sgd", null, expiresAt))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new TeamPriceQuote(ID, PRODUCT, OWNER, 1, amount, "sgd", NOW, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBadAmount() {
        Instant expiresAt = NOW.plusSeconds(900);

        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID, PRODUCT, OWNER, 1, null, "sgd", NOW, expiresAt))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));

        BigDecimal negativeAmount = new BigDecimal("-1");
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        OWNER,
                                        1,
                                        negativeAmount,
                                        "sgd",
                                        NOW,
                                        expiresAt))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));

        BigDecimal subCentAmount = new BigDecimal("1.001");
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        OWNER,
                                        1,
                                        subCentAmount,
                                        "sgd",
                                        NOW,
                                        expiresAt))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));
    }

    @Test
    void rejectsNonPositiveVersionAndNonSgd() {
        BigDecimal amount = new BigDecimal("20.00");
        Instant expiresAt = NOW.plusSeconds(900);

        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID, PRODUCT, OWNER, 0, amount, "sgd", NOW, expiresAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID, PRODUCT, OWNER, 1, amount, "usd", NOW, expiresAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyOrReversedWindow() {
        BigDecimal amount = new BigDecimal("20.00");

        assertThatThrownBy(() -> new TeamPriceQuote(ID, PRODUCT, OWNER, 1, amount, "sgd", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        Instant issuedAt = NOW.plusSeconds(900);
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID, PRODUCT, OWNER, 1, amount, "sgd", issuedAt, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
