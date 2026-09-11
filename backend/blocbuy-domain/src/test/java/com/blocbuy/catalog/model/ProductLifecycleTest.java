package com.blocbuy.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC9 invariants are tested without Spring or storage. */
class ProductLifecycleTest {
    /** A published product cannot regain deletable draft status. */
    @Test
    void publicationIsIrreversibleButDelistingIsReversible() {
        Product draft = Product.draft(UUID.randomUUID(), "SKU-1", details("10.00", 5));
        draft.requireDeletable();
        Product published = draft.publish();
        assertThat(published.status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(published.version()).isEqualTo(1);
        assertThatThrownBy(published::requireDeletable).isInstanceOf(BizException.class);
        Product delisted = published.delist();
        assertThat(delisted.status()).isEqualTo(ProductStatus.OFF_SALE);
        assertThatThrownBy(delisted::requireDeletable).isInstanceOf(BizException.class);
        assertThat(delisted.publish().status()).isEqualTo(ProductStatus.ON_SALE);
    }

    /** Editorial changes must not overwrite live inventory or reprice historical business. */
    @Test
    void commercialTermsAreEditableOnlyBeforePublication() {
        Product draft = Product.draft(UUID.randomUUID(), "SKU-2", details("10.00", 5));
        assertThat(draft.revise(details("12.00", 6)).originalPrice()).isEqualByComparingTo("12.00");
        Product published = draft.publish();
        assertThatThrownBy(() -> published.revise(details("12.00", 5)))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> published.revise(details("10.00", 6)))
                .isInstanceOf(BizException.class);
        assertThat(published.revise(details("10.00", 5)).version()).isEqualTo(2);
    }

    /** Zero-stock drafts may exist, but cannot become buyable. */
    @Test
    void invalidTransitionsAndEmptyStockCannotPublish() {
        Product draft = Product.draft(UUID.randomUUID(), "SKU-3", details("10.00", 0));
        assertThatThrownBy(draft::publish).isInstanceOf(BizException.class);
        assertThatThrownBy(draft::delist).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> details("1.001", 2)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> details("0.00", 2)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> details("1.00", -1)).isInstanceOf(BizException.class);
    }

    /** Stable fixture includes all writable fields and exact decimal money. */
    private static ListingDetails details(String price, int stock) {
        return new ListingDetails(
                "Tea",
                null,
                "Description",
                null,
                List.of(),
                "food",
                new BigDecimal(price),
                "sgd",
                stock);
    }
}
