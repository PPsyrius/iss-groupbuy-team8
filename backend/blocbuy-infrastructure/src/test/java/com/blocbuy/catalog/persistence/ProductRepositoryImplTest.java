package com.blocbuy.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.model.Product;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** UC1-ext search: same visibility/count conditions, blank normalization, no N+1. */
@ExtendWith(MockitoExtension.class)
class ProductRepositoryImplTest {

    private static final UUID PRODUCT_ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");

    @Mock private BizProductMapper mapper;
    @InjectMocks private ProductRepositoryImpl repository;

    /** Verifies identical keyword/category filters reach page and count queries. */
    @Test
    void searchPassesPatternsAndCategoryToBothQueries() {
        BizProductDO row = row();
        when(mapper.selectVisibleSearchPage(
                        eq("electronics"), eq(List.of("%Tea%")), anyInt(), anyLong()))
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
        when(mapper.selectVisibleSearchPage(isNull(), eq(expected), anyInt(), anyLong()))
                .thenReturn(List.of());
        when(mapper.countVisibleSearch(isNull(), eq(expected))).thenReturn(0L);

        repository.searchOnSale(new PageQuery(1, 20), null, List.of("100%", "cotton_\\soft"));

        verify(mapper).selectVisibleSearchPage(isNull(), eq(expected), eq(20), eq(0L));
        verify(mapper).countVisibleSearch(isNull(), eq(expected));
    }

    /** Verifies blank categories and empty token sets normalize to null filters. */
    @Test
    void searchNormalizesBlankCategoryAndEmptyPatternsToNull() {
        when(mapper.selectVisibleSearchPage(isNull(), isNull(), anyInt(), anyLong()))
                .thenReturn(List.of());
        when(mapper.countVisibleSearch(isNull(), isNull())).thenReturn(0L);

        PageResult<Product> result = repository.searchOnSale(new PageQuery(2, 5), "  ", List.of());

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> patterns = ArgumentCaptor.forClass(List.class);
        verify(mapper).selectVisibleSearchPage(isNull(), patterns.capture(), eq(5), eq(5L));
        assertThat(patterns.getValue()).isNull();
    }

    /** Verifies explicit null filters allow an unfiltered bounded search. */
    @Test
    void searchWithNullCategoryAndNullPatternsSkipsBothFilters() {
        when(mapper.selectVisibleSearchPage(isNull(), isNull(), anyInt(), anyLong()))
                .thenReturn(List.of(row()));
        when(mapper.countVisibleSearch(isNull(), isNull())).thenReturn(1L);

        PageResult<Product> result = repository.searchOnSale(new PageQuery(1, 20), null, null);

        assertThat(result.total()).isEqualTo(1);
        verify(mapper).selectVisibleSearchPage(isNull(), isNull(), eq(20), eq(0L));
        verify(mapper).countVisibleSearch(isNull(), isNull());
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
}
