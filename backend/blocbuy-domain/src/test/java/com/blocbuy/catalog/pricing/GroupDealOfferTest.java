package com.blocbuy.catalog.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Catalog-side deal view validation: identity, non-negative price, non-blank currency. */
class GroupDealOfferTest {
    private static final UUID DEAL = UUID.fromString("b0000000-0000-7000-8000-000000000001");
    private static final Instant EXPIRES = Instant.parse("2027-12-31T23:59:59Z");

    @Test
    void happyOfferPreservesAllFields() {
        GroupDealOffer offer =
                new GroupDealOffer(DEAL, new BigDecimal("89.00"), "sgd", 5, 2, EXPIRES);

        assertThat(offer.dealId()).isEqualTo(DEAL);
        assertThat(offer.dealPrice()).isEqualByComparingTo("89.00");
        assertThat(offer.currency()).isEqualTo("sgd");
        assertThat(offer.targetSize()).isEqualTo(5);
        assertThat(offer.currentSize()).isEqualTo(2);
        assertThat(offer.expireTime()).isEqualTo(EXPIRES);
    }

    @Test
    void rejectsNullDealId() {
        assertThatThrownBy(
                        () ->
                                new GroupDealOffer(
                                        null, new BigDecimal("89.00"), "sgd", 5, 2, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullOrNegativePrice() {
        assertThatThrownBy(() -> new GroupDealOffer(DEAL, null, "sgd", 5, 2, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new GroupDealOffer(DEAL, new BigDecimal("-1"), "sgd", 5, 2, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankCurrency() {
        assertThatThrownBy(
                        () ->
                                new GroupDealOffer(
                                        DEAL, new BigDecimal("89.00"), null, 5, 2, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new GroupDealOffer(
                                        DEAL, new BigDecimal("89.00"), "  ", 5, 2, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
