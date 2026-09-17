package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.query.ActiveDealView;
import com.blocbuy.catalog.query.ListingDetailView;
import com.blocbuy.catalog.query.ListingQuery;
import com.blocbuy.catalog.query.ListingView;
import com.blocbuy.catalog.service.ProductAppService;
import com.blocbuy.catalog.web.ProductController;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.identity.repository.RoleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real HTTP binding for the anonymous product browse: boundary validation of pagination params must
 * reject out-of-range values with 400 before any business code runs (issue #56 — unvalidated {@code
 * page=0} previously reached the {@code PageQuery} guard and surfaced as a 500). Only the
 * application service and the RBAC repository are mocked; the test-only session store never
 * connects to a developer's Redis.
 */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/blocbuy_placeholder?currentSchema=domain",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1"
        })
@AutoConfigureMockMvc
@Import(ProductControllerTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductControllerTest {
    private static final String PRODUCTS_PATH = "/api/v1/products";
    private static final UUID PRODUCT = UUID.fromString("01990000-0000-7000-8000-000000000001");

    @Autowired private MockMvc mvc;
    @MockitoBean private ProductAppService products;
    @MockitoBean private RoleRepository roles;

    @ParameterizedTest
    @MethodSource("invalidPagination")
    void outOfRangePaginationIsRejectedBeforeBusinessCode(String query, String field)
            throws Exception {
        mvc.perform(get(PRODUCTS_PATH + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]", containsString(field)));
        verifyNoInteractions(products);
    }

    static Stream<Arguments> invalidPagination() {
        return Stream.of(
                Arguments.of("?page=0&size=5", "page"),
                Arguments.of("?page=-1", "page"),
                Arguments.of("?size=0", "size"),
                Arguments.of("?size=101", "size"));
    }

    @Test
    void nonNumericPageUsesGlobalBadRequestMapping() throws Exception {
        mvc.perform(get(PRODUCTS_PATH + "?page=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
        verifyNoInteractions(products);
    }

    @Test
    void defaultsApplyWhenPaginationParamsAreAbsent() throws Exception {
        when(products.pageListings(null, null, null))
                .thenReturn(PageResult.of(List.of(listing()), 1, PageQuery.of(null, null)));
        mvc.perform(get(PRODUCTS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(PageQuery.DEFAULT_SIZE))
                .andExpect(jsonPath("$.data.items[0].id").value(PRODUCT.toString()));
        verify(products).pageListings(null, null, null);
    }

    @Test
    void validPaginationAndCategoryPassThrough() throws Exception {
        when(products.pageListings(2, 5, "electronics"))
                .thenReturn(PageResult.of(List.of(listing()), 6, PageQuery.of(2, 5)));
        mvc.perform(get(PRODUCTS_PATH + "?page=2&size=5&category=electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5));
        verify(products).pageListings(2, 5, "electronics");
    }

    /** Verifies HTTP keyword parameters are delegated to the search use case. */
    @Test
    void keywordSearchDelegatesToSearchUseCase() throws Exception {
        ListingQuery query = ListingQuery.of("keyboard", "electronics", 2, 5);
        when(products.search(query))
                .thenReturn(PageResult.of(List.of(listing()), 6, PageQuery.of(2, 5)));
        mvc.perform(get(PRODUCTS_PATH + "?keyword=keyboard&category=electronics&page=2&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.items[0].id").value(PRODUCT.toString()));
        verify(products).search(query);
        verifyNoMoreInteractions(products);
    }

    /** Verifies that the HTTP AI flag reaches the normalized listing query. */
    @Test
    void aiKeywordSearchCarriesOptInToSearchUseCase() throws Exception {
        ListingQuery query = ListingQuery.of("keyboard", null, null, null, true);
        when(products.search(query))
                .thenReturn(PageResult.of(List.of(listing()), 1, PageQuery.of(null, null)));

        mvc.perform(get(PRODUCTS_PATH + "?keyword=keyboard&ai=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(PRODUCT.toString()));

        verify(products).search(query);
    }

    /** Verifies that a blank HTTP keyword continues through the browse path. */
    @Test
    void blankKeywordStaysOnBrowsePath() throws Exception {
        when(products.pageListings(null, null, null))
                .thenReturn(PageResult.of(List.of(listing()), 1, PageQuery.of(null, null)));
        mvc.perform(get(PRODUCTS_PATH).param("keyword", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"));
        verify(products).pageListings(null, null, null);
    }

    /** Verifies boundary rejection of a keyword longer than the public maximum. */
    @Test
    void overlongKeywordIsRejectedBeforeBusinessCode() throws Exception {
        mvc.perform(get(PRODUCTS_PATH + "?keyword=" + "k".repeat(65)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));
        verifyNoInteractions(products);
    }

    @Test
    void getListingReturnsDetailWithDeal() throws Exception {
        when(products.getListingDetail(PRODUCT)).thenReturn(detailWithDeal());
        mvc.perform(get(PRODUCTS_PATH + "/" + PRODUCT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.id").value(PRODUCT.toString()))
                .andExpect(jsonPath("$.data.title").value("Mechanical keyboard"))
                .andExpect(jsonPath("$.data.individualPrice").value(129.00))
                .andExpect(jsonPath("$.data.groupPrice").value(99.00))
                .andExpect(jsonPath("$.data.deal.dealId").value(DEAL.toString()));
        verify(products).getListingDetail(PRODUCT);
    }

    @Test
    void getListingWithoutDealHasNullDeal() throws Exception {
        when(products.getListingDetail(PRODUCT)).thenReturn(detailWithoutDeal());
        mvc.perform(get(PRODUCTS_PATH + "/" + PRODUCT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.id").value(PRODUCT.toString()))
                .andExpect(jsonPath("$.data.deal").doesNotExist());
        verify(products).getListingDetail(PRODUCT);
    }

    @Test
    void getListingMissingProductMapsTo404() throws Exception {
        when(products.getListingDetail(PRODUCT))
                .thenThrow(new BizException(CatalogErrorCode.PRODUCT_NOT_FOUND));
        mvc.perform(get(PRODUCTS_PATH + "/" + PRODUCT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_404_001"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(products).getListingDetail(PRODUCT);
    }

    @Test
    void getListingWithInvalidUuidIsBadRequest() throws Exception {
        mvc.perform(get(PRODUCTS_PATH + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
        verifyNoInteractions(products);
    }

    @Test
    void unhandledFailureMapsToInternalErrorWithoutLeaking() throws Exception {
        when(products.getListingDetail(PRODUCT)).thenThrow(new IllegalStateException("boom"));
        mvc.perform(get(PRODUCTS_PATH + "/" + PRODUCT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_500_001"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value("Internal server error"));
        verify(products).getListingDetail(PRODUCT);
    }

    @Test
    void productDetailResponseDefaultsNullImages() {
        var response =
                new ProductController.ProductDetailResponse(
                        PRODUCT,
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

        assertThat(response.images()).isEmpty();
    }

    private static final UUID DEAL = UUID.fromString("01990000-0000-7000-8000-000000000002");

    private static ListingDetailView detailWithDeal() {
        return new ListingDetailView(
                PRODUCT,
                "Mechanical keyboard",
                "Hot-swap",
                "Full description",
                "https://img.example/cover.png",
                List.of("https://img.example/1.png"),
                "electronics",
                new BigDecimal("129.00"),
                new BigDecimal("99.00"),
                "sgd",
                5,
                1,
                new ActiveDealView(DEAL, 5, 2, Instant.parse("2027-12-31T23:59:59Z")));
    }

    private static ListingDetailView detailWithoutDeal() {
        return new ListingDetailView(
                PRODUCT,
                "Mechanical keyboard",
                "Hot-swap",
                "Full description",
                "https://img.example/cover.png",
                List.of(),
                "electronics",
                new BigDecimal("129.00"),
                null,
                "sgd",
                5,
                0,
                null);
    }

    private static ListingView listing() {
        return new ListingView(
                PRODUCT,
                "Mechanical keyboard",
                "Hot-swap",
                "https://img.example/cover.png",
                "electronics",
                new BigDecimal("129.00"),
                new BigDecimal("99.00"),
                "sgd");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SessionStore {
        @Bean(initMethod = "init", destroyMethod = "destroy")
        @Primary
        SaTokenDao testSessionDao() {
            return new SaTokenDaoDefaultImpl();
        }
    }
}
