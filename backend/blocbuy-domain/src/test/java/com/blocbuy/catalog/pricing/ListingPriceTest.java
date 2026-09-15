package com.blocbuy.catalog.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Resolved price pair: non-negative individual price, blank currency rejected. */
class ListingPriceTest {

    @Test
    void happyPairReportsGroupAvailability() {
        ListingPrice price =
                new ListingPrice(new BigDecimal("129.00"), new BigDecimal("99.00"), "sgd");

        assertThat(price.individualPrice()).isEqualByComparingTo("129.00");
        assertThat(price.groupPrice()).isEqualByComparingTo("99.00");
        assertThat(price.currency()).isEqualTo("sgd");
        assertThat(price.hasGroupPrice()).isTrue();
    }

    @Test
    void nullGroupPriceMeansIndividualOnly() {
        ListingPrice price = new ListingPrice(new BigDecimal("45.00"), null, "sgd");

        assertThat(price.groupPrice()).isNull();
        assertThat(price.hasGroupPrice()).isFalse();
    }

    @Test
    void rejectsNullOrNegativeIndividualPrice() {
        assertThatThrownBy(() -> new ListingPrice(null, null, "sgd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListingPrice(new BigDecimal("-1"), null, "sgd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankCurrency() {
        assertThatThrownBy(() -> new ListingPrice(new BigDecimal("45.00"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListingPrice(new BigDecimal("45.00"), null, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
