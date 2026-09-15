package com.blocbuy.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.pricing.DualPricingStrategy;
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
                .findBestOpenDealsByProductIds(eq(List.of(PRODUCT_ID, otherId)));
    }

    @Test
    void pageEmptyDoesSingleEmptyBatchQuery() {
        when(productRepository.pageOnSale(any(PageQuery.class), any()))
                .thenReturn(PageResult.of(List.of(), 0, new PageQuery(1, 20)));

        PageResult<ListingView> result = service.pageListings(1, 20, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verify(groupDealQueryService, times(1)).findBestOpenDealsByProductIds(eq(List.of()));
    }

    @Test
    void pagePassesCategoryAndPageQuery() {
        when(productRepository.pageOnSale(eq(new PageQuery(2, 5)), eq("books")))
                .thenReturn(PageResult.of(List.of(), 0, new PageQuery(2, 5)));

        service.pageListings(2, 5, "books");

        verify(productRepository).pageOnSale(eq(new PageQuery(2, 5)), eq("books"));
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
