package com.blocbuy.groupbuy.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.model.DealStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Group-deal reads: mapper delegation, row conversion, empty-input short-circuit. */
@ExtendWith(MockitoExtension.class)
class GroupDealRepositoryImplTest {

    private static final UUID DEAL_ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("b0000000-0000-7000-8000-000000000001");

    @Mock private BizGroupDealMapper mapper;
    @InjectMocks private GroupDealRepositoryImpl repository;

    @Test
    void hasProductReferencesDelegatesToMapper() {
        when(mapper.existsProductReference(PRODUCT_ID)).thenReturn(true);

        assertThat(repository.hasProductReferences(PRODUCT_ID)).isTrue();
        verify(mapper).existsProductReference(PRODUCT_ID);
    }

    @Test
    void findByIdConvertsPresentRow() {
        when(mapper.selectActiveById(DEAL_ID)).thenReturn(row());

        var result = repository.findById(DEAL_ID);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().id()).isEqualTo(DEAL_ID);
        assertThat(result.orElseThrow().status()).isEqualTo(DealStatus.OPEN);
    }

    @Test
    void findByIdReturnsEmptyForMissingRow() {
        assertThat(repository.findById(DEAL_ID)).isEmpty();
        verify(mapper).selectActiveById(DEAL_ID);
    }

    @Test
    void findOpenDealsReturnsEmptyWithoutQueryForNullOrEmptyIds() {
        assertThat(repository.findOpenDealsByProductIds(null)).isEmpty();
        assertThat(repository.findOpenDealsByProductIds(List.of())).isEmpty();
        verifyNoInteractions(mapper);
    }

    @Test
    void findOpenDealsConvertsBatchRows() {
        List<UUID> productIds = List.of(PRODUCT_ID);
        when(mapper.selectOpenByProductIds(productIds)).thenReturn(List.of(row()));

        var result = repository.findOpenDealsByProductIds(productIds);

        assertThat(result)
                .singleElement()
                .extracting(deal -> deal.productId())
                .isEqualTo(PRODUCT_ID);
        verify(mapper).selectOpenByProductIds(productIds);
    }

    private static BizGroupDealDO row() {
        BizGroupDealDO row = new BizGroupDealDO();
        row.setId(DEAL_ID);
        row.setDealNo("DEAL-1");
        row.setProductId(PRODUCT_ID);
        row.setLeaderUserId(UUID.fromString("c0000000-0000-7000-8000-000000000001"));
        row.setDealPrice(new BigDecimal("10.00"));
        row.setCurrency("sgd");
        row.setTargetSize(3);
        row.setCurrentSize(1);
        row.setStatus("open");
        row.setStartTime(Instant.parse("2026-09-01T00:00:00Z"));
        row.setExpireTime(Instant.parse("2026-10-01T00:00:00Z"));
        return row;
    }
}
