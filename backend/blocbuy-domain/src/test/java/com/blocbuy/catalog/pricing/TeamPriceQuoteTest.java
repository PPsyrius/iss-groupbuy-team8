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
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        null,
                                        PRODUCT,
                                        OWNER,
                                        1,
                                        new BigDecimal("20.00"),
                                        "sgd",
                                        NOW,
                                        NOW.plusSeconds(900)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        null,
                                        OWNER,
                                        1,
                                        new BigDecimal("20.00"),
                                        "sgd",
                                        NOW,
                                        NOW.plusSeconds(900)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        null,
                                        1,
                                        new BigDecimal("20.00"),
                                        "sgd",
                                        NOW,
                                        NOW.plusSeconds(900)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        OWNER,
                                        1,
                                        new BigDecimal("20.00"),
                                        "sgd",
                                        null,
                                        NOW.plusSeconds(900)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        OWNER,
                                        1,
                                        new BigDecimal("20.00"),
                                        "sgd",
                                        NOW,
                                        null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBadAmount() {
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        OWNER,
                                        1,
                                        null,
                                        "sgd",
                                        NOW,
                                        NOW.plusSeconds(900)))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        OWNER,
                                        1,
                                        new BigDecimal("-1"),
                                        "sgd",
                                        NOW,
                                        NOW.plusSeconds(900)))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        OWNER,
                                        1,
                                        new BigDecimal("1.001"),
                                        "sgd",
                                        NOW,
                                        NOW.plusSeconds(900)))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));
    }

    @Test
    void rejectsNonPositiveVersionAndNonSgd() {
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        OWNER,
                                        0,
                                        new BigDecimal("20.00"),
                                        "sgd",
                                        NOW,
                                        NOW.plusSeconds(900)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        OWNER,
                                        1,
                                        new BigDecimal("20.00"),
                                        "usd",
                                        NOW,
                                        NOW.plusSeconds(900)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyOrReversedWindow() {
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        OWNER,
                                        1,
                                        new BigDecimal("20.00"),
                                        "sgd",
                                        NOW,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new TeamPriceQuote(
                                        ID,
                                        PRODUCT,
                                        OWNER,
                                        1,
                                        new BigDecimal("20.00"),
                                        "sgd",
                                        NOW.plusSeconds(900),
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
