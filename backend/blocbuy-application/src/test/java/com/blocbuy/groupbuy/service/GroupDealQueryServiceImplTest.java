package com.blocbuy.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.gateway.OpenDealOfferCache;
import com.blocbuy.groupbuy.gateway.OpenDealOfferCache.Lookup;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.query.OpenDealOffer;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Best-offer mapping delegates selection to one bounded repository read. */
@ExtendWith(MockitoExtension.class)
class GroupDealQueryServiceImplTest {

    private static final UUID PRODUCT_ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID LEADER_ID = UUID.fromString("c0000000-0000-7000-8000-000000000001");

    @Mock private GroupDealRepository groupDealRepository;
    @Mock private OpenDealOfferCache cache;

    @InjectMocks private GroupDealQueryServiceImpl service;

    @BeforeEach
    void healthyEmptyCache() {
        lenient().when(cache.findByProductIds(any())).thenReturn(new Lookup(Map.of(), true));
    }

    @Test
    void mapsTheRepositorySelectedBestDealWithoutAnotherLookup() {
        GroupDeal selected = deal("b0000000-0000-7000-8000-000000000002", "89.00");
        when(groupDealRepository.findBestOpenDealsByProductIds(List.of(PRODUCT_ID)))
                .thenReturn(List.of(selected));

        Map<UUID, OpenDealOffer> best = service.findBestOpenDealsByProductIds(List.of(PRODUCT_ID));

        assertThat(best)
                .containsOnlyKeys(PRODUCT_ID)
                .containsEntry(
                        PRODUCT_ID,
                        new OpenDealOffer(
                                selected.id(),
                                PRODUCT_ID,
                                selected.dealPrice(),
                                "sgd",
                                5,
                                2,
                                selected.expireTime()));
        verify(groupDealRepository).findBestOpenDealsByProductIds(List.of(PRODUCT_ID));
        verifyNoMoreInteractions(groupDealRepository);
    }

    @Test
    void productWithoutOpenDealIsAbsent() {
        when(groupDealRepository.findBestOpenDealsByProductIds(List.of(PRODUCT_ID)))
                .thenReturn(List.of());

        assertThat(service.findBestOpenDealsByProductIds(List.of(PRODUCT_ID))).isEmpty();
    }

    @Test
    void emptyInputNeverHitsTheRepository() {
        assertThat(service.findBestOpenDealsByProductIds(List.of())).isEmpty();
        verifyNoInteractions(groupDealRepository);
    }

    @Test
    void nullInputNeverHitsTheRepository() {
        assertThat(service.findBestOpenDealsByProductIds(null)).isEmpty();
        verifyNoInteractions(groupDealRepository);
    }

    @Test
    void multipleProductsMapFromOneBatchWithDuplicateInputIds() {
        UUID otherProduct = UUID.fromString("a0000000-0000-7000-8000-000000000002");
        GroupDeal cheap = deal("b0000000-0000-7000-8000-000000000002", "89.00");
        GroupDeal other = dealFor(otherProduct, "b0000000-0000-7000-8000-000000000003", "10.00");
        when(groupDealRepository.findBestOpenDealsByProductIds(List.of(PRODUCT_ID, otherProduct)))
                .thenReturn(List.of(cheap, other));

        Map<UUID, OpenDealOffer> best =
                service.findBestOpenDealsByProductIds(
                        List.of(PRODUCT_ID, otherProduct, PRODUCT_ID));

        assertThat(best).containsOnlyKeys(PRODUCT_ID, otherProduct);
        assertThat(best.get(PRODUCT_ID).dealPrice()).isEqualByComparingTo("89.00");
        assertThat(best.get(otherProduct).dealPrice()).isEqualByComparingTo("10.00");
        verify(groupDealRepository)
                .findBestOpenDealsByProductIds(List.of(PRODUCT_ID, otherProduct));
        verifyNoMoreInteractions(groupDealRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void hasProductReferencesDelegatesToRepository(boolean present) {
        when(groupDealRepository.hasProductReferences(PRODUCT_ID)).thenReturn(present);

        assertThat(service.hasProductReferences(PRODUCT_ID)).isEqualTo(present);
        verify(groupDealRepository).hasProductReferences(PRODUCT_ID);
    }

    @Test
    void warmPositiveAndNegativeHitsAvoidSourceQueriesAndWrites() {
        UUID absent = UUID.randomUUID();
        GroupDeal selected = deal("b0000000-0000-7000-8000-000000000002", "89.00");
        OpenDealOffer offer =
                new OpenDealOffer(
                        selected.id(),
                        PRODUCT_ID,
                        selected.dealPrice(),
                        "sgd",
                        5,
                        2,
                        selected.expireTime());
        when(cache.findByProductIds(List.of(PRODUCT_ID, absent)))
                .thenReturn(
                        new Lookup(
                                Map.of(PRODUCT_ID, Optional.of(offer), absent, Optional.empty()),
                                true));
        assertThat(service.findBestOpenDealsByProductIds(List.of(PRODUCT_ID, absent)))
                .containsExactlyEntriesOf(Map.of(PRODUCT_ID, offer));
        verifyNoInteractions(groupDealRepository);
        verify(cache).findByProductIds(List.of(PRODUCT_ID, absent));
        verifyNoMoreInteractions(cache);
    }

    @Test
    void partialHitsLoadOnlyMissesInOneBatchAndPopulateAbsences() {
        UUID absent = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        GroupDeal selected = dealFor(fresh, "b0000000-0000-7000-8000-000000000002", "12.00");
        when(cache.findByProductIds(List.of(PRODUCT_ID, absent, fresh)))
                .thenReturn(new Lookup(Map.of(PRODUCT_ID, Optional.empty()), true));
        when(groupDealRepository.findBestOpenDealsByProductIds(List.of(absent, fresh)))
                .thenReturn(List.of(selected));
        var result =
                service.findBestOpenDealsByProductIds(List.of(PRODUCT_ID, absent, fresh, fresh));
        assertThat(result).containsOnlyKeys(fresh);
        verify(groupDealRepository).findBestOpenDealsByProductIds(List.of(absent, fresh));
        verify(cache)
                .putAll(Map.of(absent, Optional.empty(), fresh, Optional.of(result.get(fresh))));
    }

    @Test
    void unavailableLookupReturnsSourceResultWithoutAnotherRedisWait() {
        when(cache.findByProductIds(List.of(PRODUCT_ID))).thenReturn(new Lookup(Map.of(), false));
        GroupDeal selected = deal("b0000000-0000-7000-8000-000000000002", "89.00");
        when(groupDealRepository.findBestOpenDealsByProductIds(List.of(PRODUCT_ID)))
                .thenReturn(List.of(selected));
        var result = service.findBestOpenDealsByProductIds(List.of(PRODUCT_ID));
        assertThat(result.get(PRODUCT_ID).dealId()).isEqualTo(selected.id());
        verify(cache).findByProductIds(List.of(PRODUCT_ID));
        verifyNoMoreInteractions(cache);
    }

    private static GroupDeal deal(String id, String dealPrice) {
        return dealFor(PRODUCT_ID, id, dealPrice);
    }

    private static GroupDeal dealFor(UUID productId, String id, String dealPrice) {
        return GroupDeal.reconstitute(
                UUID.fromString(id),
                "DEMO-D-0001",
                productId,
                LEADER_ID,
                new BigDecimal(dealPrice),
                "sgd",
                5,
                2,
                DealStatus.OPEN,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2027-12-31T23:59:59Z"),
                null);
    }
}
