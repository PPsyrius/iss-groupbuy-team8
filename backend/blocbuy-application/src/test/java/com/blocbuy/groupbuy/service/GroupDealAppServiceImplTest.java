package com.blocbuy.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.groupbuy.gateway.ListingDisplayGateway;
import com.blocbuy.groupbuy.gateway.ListingDisplayGateway.ListingDisplay;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.MemberRole;
import com.blocbuy.groupbuy.model.TeamMembership;
import com.blocbuy.groupbuy.query.MyTeamView;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** UC8 My Teams: one page query, one batch product lookup, in-memory join, no dropped rows. */
@ExtendWith(MockitoExtension.class)
class GroupDealAppServiceImplTest {

    private static final UUID BUYER = UUID.fromString("c0000000-0000-7000-8000-000000000001");
    private static final UUID TEA = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID GONE = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final UUID ORDER = UUID.fromString("e0000000-0000-7000-8000-000000000001");
    private static final PageQuery PAGE = new PageQuery(2, 3);

    @Mock private GroupDealRepository groupDealRepository;
    @Mock private ListingDisplayGateway listingDisplayGateway;

    @InjectMocks private GroupDealAppServiceImpl service;

    @Test
    void joinsEachRowToItsListingWithOneBatchLookupOfDistinctProducts() {
        TeamMembership led = membership("1", TEA, DealStatus.SUCCEEDED, MemberRole.LEADER, ORDER);
        TeamMembership joined = membership("2", TEA, DealStatus.OPEN, MemberRole.MEMBER, null);
        when(groupDealRepository.pageMembershipsByUserId(PAGE, BUYER))
                .thenReturn(PageResult.of(List.of(led, joined), 7, PAGE));
        when(listingDisplayGateway.findByProductIds(anyCollection()))
                .thenReturn(Map.of(TEA, new ListingDisplay(TEA, "Green Tea", "tea.jpg")));

        PageResult<MyTeamView> result = service.pageMyTeams(PAGE, BUYER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(listingDisplayGateway, times(1)).findByProductIds(ids.capture());
        assertThat(ids.getValue()).containsExactly(TEA);
        assertThat(result.total()).isEqualTo(7);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.nextCursor()).isNull();
        assertThat(result.items())
                .extracting(MyTeamView::status, MyTeamView::role, MyTeamView::orderId)
                .containsExactly(
                        tuple("succeeded", "leader", ORDER), tuple("open", "member", null));
        MyTeamView first = result.items().getFirst();
        assertThat(first.dealId()).isEqualTo(led.dealId());
        assertThat(first.productId()).isEqualTo(TEA);
        assertThat(first.productTitle()).isEqualTo("Green Tea");
        assertThat(first.productImage()).isEqualTo("tea.jpg");
        assertThat(first.dealPrice()).isEqualByComparingTo("9.90");
        assertThat(first.currency()).isEqualTo("sgd");
        assertThat(first.targetSize()).isEqualTo(3);
        assertThat(first.currentSize()).isEqualTo(2);
        assertThat(first.expireTime()).isEqualTo(led.expireTime());
        assertThat(first.successTime()).isEqualTo(led.successTime());
        assertThat(first.joinTime()).isEqualTo(led.joinTime());
    }

    @Test
    void inactiveProductKeepsItsRowWithNullDisplayFields() {
        TeamMembership orphan = membership("3", GONE, DealStatus.EXPIRED, MemberRole.MEMBER, ORDER);
        when(groupDealRepository.pageMembershipsByUserId(PAGE, BUYER))
                .thenReturn(PageResult.of(List.of(orphan), 4, PAGE));
        when(listingDisplayGateway.findByProductIds(anyCollection())).thenReturn(Map.of());

        PageResult<MyTeamView> result = service.pageMyTeams(PAGE, BUYER);

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.items())
                .singleElement()
                .satisfies(
                        view -> {
                            assertThat(view.productId()).isEqualTo(GONE);
                            assertThat(view.productTitle()).isNull();
                            assertThat(view.productImage()).isNull();
                            assertThat(view.status()).isEqualTo("expired");
                        });
    }

    @Test
    void emptyPageSkipsTheProductLookupButKeepsTheTotal() {
        when(groupDealRepository.pageMembershipsByUserId(PAGE, BUYER))
                .thenReturn(PageResult.of(List.of(), 3, PAGE));

        PageResult<MyTeamView> result = service.pageMyTeams(PAGE, BUYER);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.page()).isEqualTo(2);
        verifyNoInteractions(listingDisplayGateway);
    }

    private static TeamMembership membership(
            String suffix, UUID productId, DealStatus status, MemberRole role, UUID orderId) {
        return new TeamMembership(
                UUID.fromString("b0000000-0000-7000-8000-00000000000" + suffix),
                productId,
                new BigDecimal("9.90"),
                "sgd",
                3,
                2,
                status,
                Instant.parse("2026-09-25T00:00:00Z"),
                status == DealStatus.SUCCEEDED ? Instant.parse("2026-09-24T12:00:00Z") : null,
                role,
                Instant.parse("2026-09-24T0" + suffix + ":00:00Z"),
                orderId);
    }

    /**
     * #182 review: rows, count and product lookup must share one snapshot, so {@code total} cannot
     * disagree with {@code items}. Guards the annotation against a silent edit; the snapshot
     * semantics themselves are PostgreSQL's.
     */
    @Test
    void thePageIsReadFromOneReadOnlySnapshot() throws NoSuchMethodException {
        Transactional tx =
                GroupDealAppServiceImpl.class
                        .getMethod("pageMyTeams", PageQuery.class, UUID.class)
                        .getAnnotation(Transactional.class);

        assertThat(tx.readOnly()).isTrue();
        assertThat(tx.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
