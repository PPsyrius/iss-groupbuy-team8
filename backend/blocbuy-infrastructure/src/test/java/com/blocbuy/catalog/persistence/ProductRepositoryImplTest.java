package com.blocbuy.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Product persistence: single-row write protocol, read mapping, filter normalization. */
@ExtendWith(MockitoExtension.class)
class ProductRepositoryImplTest {

    private static final UUID PRODUCT_ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("b0000000-0000-7000-8000-000000000001");

    @Mock private BizProductMapper mapper;
    @InjectMocks private ProductRepositoryImpl repository;

    @Test
    void insertDraftConvertsAndDelegates() {
        when(mapper.insertDraft(any(), ACTOR_ID)).thenReturn(1);

        repository.insertDraft(product(), ACTOR_ID);

        verify(mapper)
                .insertDraft(
                        argThat(
                                value ->
                                        PRODUCT_ID.equals(value.getId())
                                                && "DEMO-P-0001".equals(value.getProductNo())),
                        ACTOR_ID);
    }

    @Test
    void insertDraftRejectsUnexpectedAffectedRows() {
        when(mapper.insertDraft(any(), ACTOR_ID)).thenReturn(0);

        assertConflict(
                () -> repository.insertDraft(product(), ACTOR_ID),
                CatalogErrorCode.PRODUCT_NUMBER_CONFLICT);
    }

    @Test
    void updateListingDelegatesExpectedVersion() {
        when(mapper.updateListing(any(), 3, ACTOR_ID)).thenReturn(1);

        repository.updateListing(product(), 3, ACTOR_ID);

        verify(mapper).updateListing(any(), 3, ACTOR_ID);
    }

    @Test
    void updateListingRejectsVersionConflict() {
        when(mapper.updateListing(any(), 2, ACTOR_ID)).thenReturn(0);

        assertConflict(
                () -> repository.updateListing(product(), 2, ACTOR_ID),
                CatalogErrorCode.PRODUCT_VERSION_CONFLICT);
    }

    @Test
    void deleteDraftDelegatesIdentityAndVersion() {
        when(mapper.deleteDraft(PRODUCT_ID, 3, ACTOR_ID)).thenReturn(1);

        repository.deleteDraft(PRODUCT_ID, 3, ACTOR_ID);

        verify(mapper).deleteDraft(PRODUCT_ID, 3, ACTOR_ID);
    }

    @Test
    void deleteDraftRejectsVersionConflict() {
        when(mapper.deleteDraft(PRODUCT_ID, 3, ACTOR_ID)).thenReturn(2);

        assertConflict(
                () -> repository.deleteDraft(PRODUCT_ID, 3, ACTOR_ID),
                CatalogErrorCode.PRODUCT_VERSION_CONFLICT);
    }

    /** Verifies listing status filter reaches page and count queries with row conversion. */
    @ParameterizedTest
    @MethodSource("listingStatusFilters")
    void pageListingsPassesStatusFilterToBothQueries(ProductStatus status, String expectedCode) {
        when(mapper.selectListingPage(expectedCode, 5, 5L)).thenReturn(List.of(row()));
        when(mapper.countListings(expectedCode)).thenReturn(7L);

        PageResult<Product> result = repository.pageListings(new PageQuery(2, 5), status);

        assertThat(result.items()).singleElement().extracting(Product::id).isEqualTo(PRODUCT_ID);
        assertThat(result.total()).isEqualTo(7);
        verify(mapper).selectListingPage(expectedCode, 5, 5L);
        verify(mapper).countListings(expectedCode);
    }

    static Stream<Arguments> listingStatusFilters() {
        return Stream.of(Arguments.of(ProductStatus.ON_SALE, "on_sale"), Arguments.of(null, null));
    }

    @Test
    void lockByIdConvertsPresentAndMissingRows() {
        doReturn(row()).doReturn(null).when(mapper).selectActiveByIdForUpdate(PRODUCT_ID);

        assertThat(repository.lockById(PRODUCT_ID)).isPresent();
        assertThat(repository.lockById(PRODUCT_ID)).isEmpty();
        verify(mapper, times(2)).selectActiveByIdForUpdate(PRODUCT_ID);
    }

    @Test
    void findByIdConvertsPresentAndMissingRows() {
        doReturn(row()).doReturn(null).when(mapper).selectActiveById(PRODUCT_ID);

        assertThat(repository.findById(PRODUCT_ID)).isPresent();
        assertThat(repository.findById(PRODUCT_ID)).isEmpty();
        verify(mapper, times(2)).selectActiveById(PRODUCT_ID);
    }

    /** Verifies on-sale category filter reaches page and count queries with row conversion. */
    @ParameterizedTest
    @MethodSource("onSaleCategories")
    void pageOnSalePassesCategoryFilterToBothQueries(String category, String expectedCode) {
        when(mapper.selectOnSalePage(expectedCode, 20, 0L)).thenReturn(List.of(row()));
        when(mapper.countOnSale(expectedCode)).thenReturn(6L);

        PageResult<Product> result = repository.pageOnSale(new PageQuery(1, 20), category);

        assertThat(result.items()).singleElement().extracting(Product::id).isEqualTo(PRODUCT_ID);
        assertThat(result.total()).isEqualTo(6);
        verify(mapper).selectOnSalePage(expectedCode, 20, 0L);
        verify(mapper).countOnSale(expectedCode);
    }

    static Stream<Arguments> onSaleCategories() {
        return Stream.of(
                Arguments.of("electronics", "electronics"),
                Arguments.of(null, null),
                Arguments.of("  ", null));
    }

    /** Verifies identical keyword/category filters reach page and count queries. */
    @Test
    void searchPassesPatternsAndCategoryToBothQueries() {
        BizProductDO row = row();
        when(mapper.selectVisibleSearchPage("electronics", List.of("%Tea%"), anyInt(), anyLong()))
                .thenReturn(List.of(row));
        when(mapper.countVisibleSearch("electronics", List.of("%Tea%"))).thenReturn(1L);

        PageResult<Product> result =
                repository.searchOnSale(new PageQuery(1, 20), "electronics", List.of("Tea"));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).title()).isEqualTo("Green Tea");
        verify(mapper).selectVisibleSearchPage("electronics", List.of("%Tea%"), 20, 0L);
        verify(mapper).countVisibleSearch("electronics", List.of("%Tea%"));
    }

    /** Verifies SQL LIKE metacharacters are escaped before mapper delegation. */
    @Test
    void searchEscapesLikeMetacharactersInInfrastructure() {
        List<String> expected = List.of("%100\\%%", "%cotton\\_\\\\soft%");
        when(mapper.selectVisibleSearchPage(isNull(), expected, anyInt(), anyLong()))
                .thenReturn(List.of());
        when(mapper.countVisibleSearch(isNull(), expected)).thenReturn(0L);

        repository.searchOnSale(new PageQuery(1, 20), null, List.of("100%", "cotton_\\soft"));

        verify(mapper).selectVisibleSearchPage(isNull(), expected, 20, 0L);
        verify(mapper).countVisibleSearch(isNull(), expected);
    }

    /** Verifies blank and null search filters normalize to null for page and count queries. */
    @ParameterizedTest
    @MethodSource("blankOrNullFilters")
    void searchNormalizesBlankAndNullFiltersToNull(String category, List<String> keywords) {
        when(mapper.selectVisibleSearchPage(isNull(), isNull(), anyInt(), anyLong()))
                .thenReturn(List.of());
        when(mapper.countVisibleSearch(isNull(), isNull())).thenReturn(0L);

        PageResult<Product> result =
                repository.searchOnSale(new PageQuery(1, 20), category, keywords);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verify(mapper).selectVisibleSearchPage(isNull(), isNull(), 20, 0L);
        verify(mapper).countVisibleSearch(isNull(), isNull());
    }

    static Stream<Arguments> blankOrNullFilters() {
        return Stream.of(Arguments.of("  ", List.of()), Arguments.of(null, null));
    }

    /** Builds a complete persistence row for repository conversion assertions. */
    private static BizProductDO row() {
        BizProductDO row = new BizProductDO();
        row.setId(PRODUCT_ID);
        row.setProductNo("DEMO-P-0001");
        row.setTitle("Green Tea");
        row.setSubtitle("subtitle");
        row.setDescription("description");
        row.setCoverImage("https://cdn.example.com/cover.jpg");
        row.setImages("[\"https://cdn.example.com/1.jpg\"]");
        row.setCategory("electronics");
        row.setOriginalPrice(new BigDecimal("45.00"));
        row.setCurrency("sgd");
        row.setStock(10);
        row.setSoldCount(3);
        row.setStatus("on_sale");
        row.setPricingMode("dual");
        row.setVersion(0);
        return row;
    }

    private static Product product() {
        return ProductConverter.toDomain(row());
    }

    private static void assertConflict(Runnable action, CatalogErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(expected);
    }
}
