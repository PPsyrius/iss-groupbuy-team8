package com.blocbuy.catalog.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** View null-tolerance: absent images and absent deals map to safe defaults. */
class ListingDetailViewTest {

    @Test
    void nullImagesAndDealDefaultToEmpty() {
        ListingDetailView view =
                new ListingDetailView(
                        UUID.randomUUID(),
                        "Mechanical keyboard",
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("129.00"),
                        null,
                        "sgd",
                        5,
                        0,
                        null);

        assertThat(view.images()).isEmpty();
        assertThat(view.deal()).isNull();
    }
}
