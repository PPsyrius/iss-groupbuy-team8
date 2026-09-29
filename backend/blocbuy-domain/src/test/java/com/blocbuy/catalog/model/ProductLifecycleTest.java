package com.blocbuy.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.catalog.exception.CatalogErrorCode;
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
        assertThatThrownBy(published::requireDeletable)
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_STATE_CONFLICT));
        Product delisted = published.delist();
        assertThat(delisted.status()).isEqualTo(ProductStatus.OFF_SALE);
        assertThatThrownBy(delisted::requireDeletable)
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_STATE_CONFLICT));
        assertThat(delisted.publish().status()).isEqualTo(ProductStatus.ON_SALE);
    }

    /** Editorial changes must not overwrite live inventory or reprice historical business. */
    @Test
    void commercialTermsAreEditableOnlyBeforePublication() {
        Product draft = Product.draft(UUID.randomUUID(), "SKU-2", details("10.00", 5));
        assertThat(draft.revise(details("12.00", 6)).originalPrice()).isEqualByComparingTo("12.00");
        Product published = draft.publish();
        ListingDetails repriced = details("12.00", 5);
        assertThatThrownBy(() -> published.revise(repriced))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_STATE_CONFLICT));
        ListingDetails restocked = details("10.00", 6);
        assertThatThrownBy(() -> published.revise(restocked))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_STATE_CONFLICT));
        assertThat(published.revise(details("10.00", 5)).version()).isEqualTo(2);
    }

    /** Zero-stock drafts may exist, but cannot become buyable. */
    @Test
    void invalidTransitionsAndEmptyStockCannotPublish() {
        Product draft = Product.draft(UUID.randomUUID(), "SKU-3", details("10.00", 0));
        assertThatThrownBy(draft::publish)
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_STATE_CONFLICT));
        assertThatThrownBy(draft::delist)
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_STATE_CONFLICT));
        assertThatThrownBy(() -> details("1.001", 2))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_INVALID));
        assertThatThrownBy(() -> details("0.00", 2))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_INVALID));
        assertThatThrownBy(() -> details("1.00", -1))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_INVALID));
    }

    /** Terms are editable in every lifecycle state and the write is a new revision. */
    @Test
    void teamTermsAreConfigurableInEveryStateAsANewRevision() {
        UUID id = UUID.randomUUID();
        Product draft = Product.draft(id, "SKU-T", details("10.00", 5));
        assertThat(draft.teamTerms().configured()).isFalse();
        assertThat(draft.teamTerms().targetSize()).isNull();

        Product configured = draft.configureTeamTerms(3, 7200);
        assertThat(configured.teamTerms()).isEqualTo(new ProductTeamTerms(id, 3, 7200));
        assertThat(configured.teamTerms().configured()).isTrue();
        assertThat(configured.version()).isEqualTo(1);
        assertThat(configured.title()).isEqualTo(draft.title());
        assertThat(draft.version()).isZero();
        assertThat(draft.teamTerms().configured()).isFalse();

        Product published = configured.publish();
        Product live = published.configureTeamTerms(4, 3600);
        assertThat(live.teamTerms()).isEqualTo(new ProductTeamTerms(id, 4, 3600));
        assertThat(live.status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(live.delist().configureTeamTerms(5, 2400).teamTerms().targetSize()).isEqualTo(5);
    }

    /** Terms survive every other revision; only the terms command changes them. */
    @Test
    void otherRevisionsCarryTheTermsThrough() {
        Product configured =
                Product.draft(UUID.randomUUID(), "SKU-C", details("10.00", 5))
                        .configureTeamTerms(3, 7200);
        ProductTeamTerms terms = configured.teamTerms();
        assertThat(configured.revise(details("10.00", 5)).teamTerms()).isEqualTo(terms);
        assertThat(configured.publish().teamTerms()).isEqualTo(terms);
        assertThat(configured.publish().delist().teamTerms()).isEqualTo(terms);
    }

    /** A rejected command reports each bad term field and changes nothing. */
    @Test
    void invalidTermsAreRejectedWithEachViolatedField() {
        Product draft = Product.draft(UUID.randomUUID(), "SKU-I", details("10.00", 5));
        assertThatThrownBy(() -> draft.configureTeamTerms(1, 10))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> {
                            assertThat(ex.errorCode())
                                    .isEqualTo(CatalogErrorCode.TEAM_TERMS_INVALID);
                            assertThat(ex.details())
                                    .containsOnlyKeys("targetSize", "lifetimeSeconds");
                        });
        assertThat(draft.version()).isZero();
    }

    /** The version token never wraps, even for the terms command. */
    @Test
    void exhaustedVersionRefusesTheTermsCommand() {
        Product exhausted =
                Product.reconstitute(
                        UUID.randomUUID(),
                        "SKU-M",
                        "Tea",
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        new BigDecimal("10.00"),
                        "sgd",
                        5,
                        0,
                        ProductStatus.DRAFT,
                        PricingMode.DUAL,
                        Integer.MAX_VALUE);
        assertThatThrownBy(() -> exhausted.configureTeamTerms(2, 2400))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_VERSION_CONFLICT));
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
