package com.blocbuy.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.gateway.ListingSearchReranker;
import com.blocbuy.catalog.gateway.ListingSearchReranker.Candidate;
import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.pricing.DualPricingStrategy;
import com.blocbuy.catalog.query.ListingQuery;
import com.blocbuy.catalog.query.ListingView;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.groupbuy.service.GroupDealQueryService;
import com.blocbuy.groupbuy.service.GroupDealQueryService.OpenDealOffer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Listing browse: dual price per view, batch deal lookup, no existence leak for off-sale rows. */
@ExtendWith(MockitoExtension.class)
class ProductAppServiceImplTest {

    private static final UUID PRODUCT_ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID DEAL_ID = UUID.fromString("b0000000-0000-7000-8000-000000000001");

    @Mock private ProductRepository productRepository;
    @Mock private GroupDealQueryService groupDealQueryService;
    @Mock private PricingStrategyResolver pricingStrategyResolver;
    @Mock private ListingSearchReranker listingSearchReranker;

    @InjectMocks private ProductAppServiceImpl service;

    @Test
    void pageListingsResolvesDualPricePerItemWithOneBatchDealQuery() {
        Product withDeal = product(PRODUCT_ID, "129.00", ProductStatus.ON_SALE);
        UUID otherId = UUID.fromString("a0000000-0000-7000-8000-000000000002");
        Product withoutDeal = product(otherId, "45.00", ProductStatus.ON_SALE);
        when(productRepository.pageOnSale(any(PageQuery.class), any()))
                .thenReturn(PageResult.of(List.of(withDeal, withoutDeal), 2, new PageQuery(1, 20)));
        when(pricingStrategyResolver.strategyFor(PricingMode.DUAL))
                .thenReturn(new DualPricingStrategy());
        when(groupDealQueryService.findBestOpenDealsByProductIds(List.of(PRODUCT_ID, otherId)))
                .thenReturn(Map.of(PRODUCT_ID, offer(PRODUCT_ID, "89.00")));

        PageResult<ListingView> result = service.pageListings(null, null, null);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items().get(0).individualPrice()).isEqualByComparingTo("129.00");
        assertThat(result.items().get(0).groupPrice()).isEqualByComparingTo("89.00");
        assertThat(result.items().get(1).groupPrice()).isNull();
        // one batch call for the whole page — never per item
        verify(groupDealQueryService, times(1))
                .findBestOpenDealsByProductIds(List.of(PRODUCT_ID, otherId));
    }

    @Test
    void pageEmptyDoesSingleEmptyBatchQuery() {
        when(productRepository.pageOnSale(any(PageQuery.class), any()))
                .thenReturn(PageResult.of(List.of(), 0, new PageQuery(1, 20)));

        PageResult<ListingView> result = service.pageListings(1, 20, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verify(groupDealQueryService, times(1)).findBestOpenDealsByProductIds(List.of());
    }

    @Test
    void pagePassesCategoryAndPageQuery() {
        PageQuery pageQuery = new PageQuery(2, 5);
        when(productRepository.pageOnSale(pageQuery, "books"))
                .thenReturn(PageResult.of(List.of(), 0, new PageQuery(2, 5)));

        service.pageListings(2, 5, "books");

        verify(productRepository).pageOnSale(pageQuery, "books");
    }

    @Test
    void listingDetailReturnsDualPriceAndDealProgress() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product(PRODUCT_ID, "129.00", ProductStatus.ON_SALE)));
        when(pricingStrategyResolver.strategyFor(PricingMode.DUAL))
                .thenReturn(new DualPricingStrategy());
        when(groupDealQueryService.findBestOpenDealsByProductIds(List.of(PRODUCT_ID)))
                .thenReturn(Map.of(PRODUCT_ID, offer(PRODUCT_ID, "89.00")));

        var detail = service.getListingDetail(PRODUCT_ID);

        assertThat(detail.individualPrice()).isEqualByComparingTo("129.00");
        assertThat(detail.groupPrice()).isEqualByComparingTo("89.00");
        assertThat(detail.deal().dealId()).isEqualTo(DEAL_ID);
        assertThat(detail.deal().currentSize()).isEqualTo(2);
        assertThat(detail.deal().targetSize()).isEqualTo(5);
    }

    @Test
    void listingDetailWithoutDealShowsIndividualPriceOnly() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product(PRODUCT_ID, "45.00", ProductStatus.ON_SALE)));
        when(pricingStrategyResolver.strategyFor(PricingMode.DUAL))
                .thenReturn(new DualPricingStrategy());
        when(groupDealQueryService.findBestOpenDealsByProductIds(List.of(PRODUCT_ID)))
                .thenReturn(Map.of());

        var detail = service.getListingDetail(PRODUCT_ID);

        assertThat(detail.groupPrice()).isNull();
        assertThat(detail.deal()).isNull();
    }

    @Test
    void listingDetailRejectsMissingProduct() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getListingDetail(PRODUCT_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void listingDetailHidesOffSaleProductAsNotFound() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product(PRODUCT_ID, "45.00", ProductStatus.OFF_SALE)));

        assertThatThrownBy(() -> service.getListingDetail(PRODUCT_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND);
    }

    /** Verifies keyword/category delegation and one batched deal enrichment query. */
    @Test
    void searchCombinesKeywordAndCategoryWithOneBatchDealQuery() {
        Product match = product(PRODUCT_ID, "129.00", ProductStatus.ON_SALE);
        ListingQuery query = ListingQuery.of("headphones", "electronics", 1, 20);
        when(productRepository.searchOnSale(
                        new PageQuery(1, 20), "electronics", List.of("headphones")))
                .thenReturn(PageResult.of(List.of(match), 1, new PageQuery(1, 20)));
        when(pricingStrategyResolver.strategyFor(PricingMode.DUAL))
                .thenReturn(new DualPricingStrategy());
        when(groupDealQueryService.findBestOpenDealsByProductIds(List.of(PRODUCT_ID)))
                .thenReturn(Map.of(PRODUCT_ID, offer(PRODUCT_ID, "89.00")));

        PageResult<ListingView> result = service.search(query);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).groupPrice()).isEqualByComparingTo("89.00");
        verify(groupDealQueryService, times(1)).findBestOpenDealsByProductIds(List.of(PRODUCT_ID));
        verifyNoInteractions(listingSearchReranker);
    }

    /** Verifies that semantic order is applied before batched listing enrichment. */
    @Test
    void aiSearchReranksPageBeforeBatchEnrichment() {
        UUID secondId = UUID.fromString("a0000000-0000-7000-8000-000000000002");
        Product first = product(PRODUCT_ID, "129.00", ProductStatus.ON_SALE);
        Product second = product(secondId, "45.00", ProductStatus.ON_SALE);
        ListingQuery query = ListingQuery.of("headphones", null, 1, 20, true);
        when(productRepository.searchOnSale(new PageQuery(1, 20), null, List.of("headphones")))
                .thenReturn(PageResult.of(List.of(first, second), 2, new PageQuery(1, 20)));
        when(listingSearchReranker.rerank(
                        "headphones",
                        List.of(
                                new Candidate(
                                        PRODUCT_ID,
                                        "Headphones",
                                        "subtitle",
                                        "description",
                                        "electronics"),
                                new Candidate(
                                        secondId,
                                        "Headphones",
                                        "subtitle",
                                        "description",
                                        "electronics"))))
                .thenReturn(List.of(secondId, PRODUCT_ID));
        when(pricingStrategyResolver.strategyFor(PricingMode.DUAL))
                .thenReturn(new DualPricingStrategy());
        when(groupDealQueryService.findBestOpenDealsByProductIds(List.of(secondId, PRODUCT_ID)))
                .thenReturn(Map.of());

        PageResult<ListingView> result = service.search(query);

        assertThat(result.items())
                .extracting(ListingView::id)
                .containsExactly(secondId, PRODUCT_ID);
        verify(groupDealQueryService).findBestOpenDealsByProductIds(List.of(secondId, PRODUCT_ID));
    }

    /** Verifies that unknown IDs are ignored and omitted products retain SQL order. */
    @Test
    void aiSearchIgnoresUnknownIdsAndAppendsOmittedCandidatesInSqlOrder() {
        UUID secondId = UUID.fromString("a0000000-0000-7000-8000-000000000002");
        UUID unknownId = UUID.fromString("a0000000-0000-7000-8000-000000000099");
        Product first = product(PRODUCT_ID, "129.00", ProductStatus.ON_SALE);
        Product second = product(secondId, "45.00", ProductStatus.ON_SALE);
        ListingQuery query = ListingQuery.of("headphones", null, 1, 20, true);
        when(productRepository.searchOnSale(new PageQuery(1, 20), null, List.of("headphones")))
                .thenReturn(PageResult.of(List.of(first, second), 2, new PageQuery(1, 20)));
        when(listingSearchReranker.rerank(any(), any())).thenReturn(List.of(unknownId, secondId));
        when(pricingStrategyResolver.strategyFor(PricingMode.DUAL))
                .thenReturn(new DualPricingStrategy());
        when(groupDealQueryService.findBestOpenDealsByProductIds(List.of(secondId, PRODUCT_ID)))
                .thenReturn(Map.of());

        PageResult<ListingView> result = service.search(query);

        assertThat(result.items())
                .extracting(ListingView::id)
                .containsExactly(secondId, PRODUCT_ID);
    }

    /** Verifies that a one-item page does not call the semantic provider. */
    @Test
    void aiSearchSkipsProviderForSingleCandidate() {
        Product match = product(PRODUCT_ID, "129.00", ProductStatus.ON_SALE);
        ListingQuery query = ListingQuery.of("headphones", null, 1, 20, true);
        when(productRepository.searchOnSale(new PageQuery(1, 20), null, List.of("headphones")))
                .thenReturn(PageResult.of(List.of(match), 1, new PageQuery(1, 20)));
        when(pricingStrategyResolver.strategyFor(PricingMode.DUAL))
                .thenReturn(new DualPricingStrategy());
        when(groupDealQueryService.findBestOpenDealsByProductIds(List.of(PRODUCT_ID)))
                .thenReturn(Map.of());

        service.search(query);

        verifyNoInteractions(listingSearchReranker);
    }

    /** Verifies that a normalized blank keyword bypasses semantic ranking. */
    @Test
    void aiSearchSkipsProviderWhenNormalizedKeywordIsNull() {
        UUID secondId = UUID.fromString("a0000000-0000-7000-8000-000000000002");
        Product first = product(PRODUCT_ID, "129.00", ProductStatus.ON_SALE);
        Product second = product(secondId, "45.00", ProductStatus.ON_SALE);
        ListingQuery query = ListingQuery.of("\u2003", null, 1, 20, true);

        when(productRepository.searchOnSale(new PageQuery(1, 20), null, List.of()))
                .thenReturn(PageResult.of(List.of(first, second), 2, new PageQuery(1, 20)));
        when(pricingStrategyResolver.strategyFor(PricingMode.DUAL))
                .thenReturn(new DualPricingStrategy());
        when(groupDealQueryService.findBestOpenDealsByProductIds(List.of(PRODUCT_ID, secondId)))
                .thenReturn(Map.of());

        PageResult<ListingView> result = service.search(query);

        assertThat(result.items())
                .extracting(ListingView::id)
                .containsExactly(PRODUCT_ID, secondId);
        verifyNoInteractions(listingSearchReranker);
    }

    /** Verifies fail-open SQL ordering when the semantic port returns no ranking. */
    @Test
    void aiSearchPreservesSqlOrderWhenProviderReturnsNull() {
        UUID secondId = UUID.fromString("a0000000-0000-7000-8000-000000000002");
        Product first = product(PRODUCT_ID, "129.00", ProductStatus.ON_SALE);
        Product second = product(secondId, "45.00", ProductStatus.ON_SALE);
        ListingQuery query = ListingQuery.of("headphones", null, 1, 20, true);
        when(productRepository.searchOnSale(new PageQuery(1, 20), null, List.of("headphones")))
                .thenReturn(PageResult.of(List.of(first, second), 2, new PageQuery(1, 20)));
        when(listingSearchReranker.rerank(any(), any())).thenReturn(null);
        when(pricingStrategyResolver.strategyFor(PricingMode.DUAL))
                .thenReturn(new DualPricingStrategy());
        when(groupDealQueryService.findBestOpenDealsByProductIds(List.of(PRODUCT_ID, secondId)))
                .thenReturn(Map.of());

        PageResult<ListingView> result = service.search(query);

        assertThat(result.items())
                .extracting(ListingView::id)
                .containsExactly(PRODUCT_ID, secondId);
    }

    /** Verifies that an empty search page remains valid and provider-free. */
    @Test
    void searchEmptyResultDoesNotFail() {
        ListingQuery query = ListingQuery.of("no-such-thing", null, 1, 20);
        when(productRepository.searchOnSale(new PageQuery(1, 20), null, List.of("no-such-thing")))
                .thenReturn(PageResult.of(List.of(), 0, new PageQuery(1, 20)));

        PageResult<ListingView> result = service.search(query);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verify(groupDealQueryService, times(1)).findBestOpenDealsByProductIds(List.of());
        verifyNoInteractions(listingSearchReranker);
    }

    private static Product product(UUID id, String originalPrice, ProductStatus status) {
        return Product.reconstitute(
                id,
                "DEMO-P-0001",
                "Headphones",
                "subtitle",
                "description",
                "https://cdn.example.com/cover.jpg",
                List.of("https://cdn.example.com/1.jpg"),
                "electronics",
                new BigDecimal(originalPrice),
                "sgd",
                10,
                3,
                status,
                PricingMode.DUAL);
    }

    private static OpenDealOffer offer(UUID productId, String dealPrice) {
        return new OpenDealOffer(
                DEAL_ID,
                productId,
                new BigDecimal(dealPrice),
                "sgd",
                5,
                2,
                Instant.parse("2027-12-31T23:59:59Z"));
    }
}
