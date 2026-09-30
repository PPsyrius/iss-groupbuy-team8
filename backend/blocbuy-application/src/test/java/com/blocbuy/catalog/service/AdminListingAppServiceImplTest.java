package com.blocbuy.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.ListingDetails;
import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.catalog.repository.TeamPriceRepository;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.groupbuy.service.GroupDealQueryService;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.service.UserAppService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Orchestration proofs supplement real HTTP/database acceptance tests. */
@ExtendWith(MockitoExtension.class)
class AdminListingAppServiceImplTest {
    private static final UUID ID = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    @Mock private ProductRepository products;
    @Mock private TeamPriceRepository prices;
    @Mock private GroupDealQueryService deals;
    @Mock private UserAppService users;
    private AdminListingAppServiceImpl service;

    /** Use real domain objects with only external ports mocked. */
    @BeforeEach
    void setUp() {
        service = new AdminListingAppServiceImpl(products, prices, deals, users);
    }

    /** Account validation precedes creating server-owned draft identity. */
    @Test
    void creationUsesUuidV7AndDraftDefaults() {
        when(products.resolveProductNumber("SKU")).thenReturn("SKU");
        Product created = service.create("SKU", details(), ACTOR);
        assertThat(created.id().version()).isEqualTo(7);
        assertThat(created.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(created.soldCount()).isZero();
        verify(products).insertDraft(created, ACTOR);
    }

    /** Missing numbers are resolved only after checking the actor's account. */
    @Test
    void creationResolvesMissingProductNumberBeforeInsertion() {
        when(products.resolveProductNumber(null)).thenReturn("BB-100001");
        Product created = service.create(null, details(), ACTOR);
        assertThat(created.productNo()).isEqualTo("BB-100001");
        var order = inOrder(users, products);
        order.verify(users).requireActive(ACTOR);
        order.verify(products).resolveProductNumber(null);
        order.verify(products).insertDraft(created, ACTOR);
    }

    /** Product lock precedes account/reference checks and the conditional delete. */
    @Test
    void deletionUsesSharedProductFirstLockOrder() {
        when(products.lockById(ID)).thenReturn(Optional.of(draft()));
        service.delete(ID, 0, ACTOR);
        var order = inOrder(products, users, prices, deals);
        order.verify(products).lockById(ID);
        order.verify(users).requireActive(ACTOR);
        order.verify(prices).hasProductReferences(ID);
        order.verify(deals).hasProductReferences(ID);
        order.verify(products).deleteDraft(ID, 0, ACTOR);
    }

    /** Neither a stale version nor a business reference may write the row. */
    @Test
    void staleAndReferencedDraftsAreRejected() {
        when(products.lockById(ID)).thenReturn(Optional.of(draft()));
        assertThatThrownBy(() -> service.delete(ID, 1, ACTOR)).isInstanceOf(BizException.class);
        when(prices.hasProductReferences(ID)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(ID, 0, ACTOR)).isInstanceOf(BizException.class);
        verify(products, never()).deleteDraft(ID, 0, ACTOR);
        verify(deals, never()).hasProductReferences(any());
    }

    /** A deal reference alone vetoes deletion even when no price history exists. */
    @Test
    void dealReferenceVetoesDraftDeletion() {
        when(products.lockById(ID)).thenReturn(Optional.of(draft()));
        when(prices.hasProductReferences(ID)).thenReturn(false);
        when(deals.hasProductReferences(ID)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(ID, 0, ACTOR))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_REFERENCED);
        verify(products, never()).deleteDraft(any(), anyInt(), any());
    }

    /** Missing resources do not become drafts implicitly. */
    @Test
    void missingProductCannotBeRevisedOrPublished() {
        ListingDetails content = details();
        assertThatThrownBy(() -> service.revise(ID, content, 0, ACTOR))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.changeStatus(ID, ProductStatus.ON_SALE, 0, ACTOR))
                .isInstanceOf(BizException.class);
    }

    /** The use-case layer rechecks active state rather than trusting a stale session. */
    @Test
    void disabledActorCannotCreate() {
        doThrow(new BizException(IdentityErrorCode.ACCOUNT_DISABLED))
                .when(users)
                .requireActive(ACTOR);
        ListingDetails content = details();
        assertThatThrownBy(() -> service.create("SKU", content, ACTOR))
                .isInstanceOf(BizException.class);
        verify(products, never()).insertDraft(any(), any());
    }

    /** Administrative pagination is a single repository call, not one query per result. */
    @Test
    void pageDelegatesOneBoundedRead() {
        PageQuery query = new PageQuery(1, 20);
        when(products.pageListings(query, ProductStatus.DRAFT))
                .thenReturn(PageResult.of(List.of(draft()), 1, query));
        assertThat(service.page(query, ProductStatus.DRAFT, ACTOR).total()).isEqualTo(1);
        verify(products).pageListings(query, ProductStatus.DRAFT);
    }

    /** Administrative reads check the account first, then return the stored aggregate. */
    @Test
    void getReturnsProductAfterActiveCheck() {
        Product stored = draft();
        when(products.findById(ID)).thenReturn(Optional.of(stored));

        assertThat(service.get(ID, ACTOR)).isEqualTo(stored);
        var order = inOrder(users, products);
        order.verify(users).requireActive(ACTOR);
        order.verify(products).findById(ID);
    }

    /** Missing rows surface as not-found rather than an empty draft. */
    @Test
    void getMissingProductIsNotFound() {
        when(products.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID, ACTOR))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND);
    }

    /** Editorial revision preserves identity and writes under the expected version. */
    @Test
    void reviseAppliesEditorialChangeAndWrites() {
        when(products.lockById(ID)).thenReturn(Optional.of(draft()));
        ListingDetails revisedDetails =
                new ListingDetails(
                        "Green Tea",
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        new BigDecimal("12.00"),
                        "sgd",
                        5);

        Product revised = service.revise(ID, revisedDetails, 0, ACTOR);

        assertThat(revised.title()).isEqualTo("Green Tea");
        assertThat(revised.version()).isEqualTo(1);
        verify(products).updateListing(revised, 0, ACTOR);
    }

    /** Publishing moves a stocked draft to on-sale under the expected version. */
    @Test
    void changeStatusPublishesDraftToOnSale() {
        when(products.lockById(ID)).thenReturn(Optional.of(draft()));

        Product published = service.changeStatus(ID, ProductStatus.ON_SALE, 0, ACTOR);

        assertThat(published.status()).isEqualTo(ProductStatus.ON_SALE);
        verify(products).updateListing(published, 0, ACTOR);
    }

    /** Delisting moves a published listing to off-sale under the expected version. */
    @Test
    void changeStatusDelistsOnSaleToOffSale() {
        Product onSale =
                Product.reconstitute(
                        ID,
                        "SKU",
                        "Tea",
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        new BigDecimal("12.00"),
                        "sgd",
                        5,
                        0,
                        ProductStatus.ON_SALE,
                        PricingMode.DUAL);
        when(products.lockById(ID)).thenReturn(Optional.of(onSale));

        Product delisted = service.changeStatus(ID, ProductStatus.OFF_SALE, 0, ACTOR);

        assertThat(delisted.status()).isEqualTo(ProductStatus.OFF_SALE);
        verify(products).updateListing(delisted, 0, ACTOR);
    }

    /** The draft state is terminal as a target; delist/publish carry the transition. */
    @Test
    void changeStatusToDraftIsRejected() {
        when(products.lockById(ID)).thenReturn(Optional.of(draft()));

        assertThatThrownBy(() -> service.changeStatus(ID, ProductStatus.DRAFT, 0, ACTOR))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_STATE_CONFLICT);
        verify(products, never()).updateListing(any(), anyInt(), any());
    }

    /** Stable never-published fixture. */
    private static Product draft() {
        return Product.draft(ID, "SKU", details());
    }

    /** Exact-money fixture that matches the supported first-team quotation currency. */
    private static ListingDetails details() {
        return new ListingDetails(
                "Tea", null, null, null, List.of(), null, new BigDecimal("12.00"), "sgd", 5);
    }
}
