package com.blocbuy.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import com.blocbuy.groupbuy.service.GroupDealQueryService.OpenDealOffer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Best-open-deal selection: lowest deal price per product wins; empty input short-circuits. */
@ExtendWith(MockitoExtension.class)
class GroupDealQueryServiceImplTest {

    private static final UUID PRODUCT_ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID LEADER_ID = UUID.fromString("c0000000-0000-7000-8000-000000000001");

    @Mock private GroupDealRepository groupDealRepository;

    @InjectMocks private GroupDealQueryServiceImpl service;

    @Test
    void lowestPricedOpenDealWinsPerProduct() {
        GroupDeal expensive = deal("b0000000-0000-7000-8000-000000000001", "99.00");
        GroupDeal cheap = deal("b0000000-0000-7000-8000-000000000002", "89.00");
        when(groupDealRepository.findOpenDealsByProductIds(List.of(PRODUCT_ID)))
                .thenReturn(List.of(expensive, cheap));

        Map<UUID, OpenDealOffer> best = service.findBestOpenDealsByProductIds(List.of(PRODUCT_ID));

        assertThat(best).containsOnlyKeys(PRODUCT_ID);
        assertThat(best.get(PRODUCT_ID).dealPrice()).isEqualByComparingTo("89.00");
        assertThat(best.get(PRODUCT_ID).dealId()).isEqualTo(cheap.id());
    }

    @Test
    void productWithoutOpenDealIsAbsent() {
        when(groupDealRepository.findOpenDealsByProductIds(List.of(PRODUCT_ID)))
                .thenReturn(List.of());

        assertThat(service.findBestOpenDealsByProductIds(List.of(PRODUCT_ID))).isEmpty();
    }

    @Test
    void emptyInputNeverHitsTheRepository() {
        assertThat(service.findBestOpenDealsByProductIds(List.of())).isEmpty();
        verifyNoInteractions(groupDealRepository);
    }

    private static GroupDeal deal(String id, String dealPrice) {
        return GroupDeal.reconstitute(
                UUID.fromString(id),
                "DEMO-D-0001",
                PRODUCT_ID,
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
