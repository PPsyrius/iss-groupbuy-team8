package com.blocbuy.groupbuy.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.MemberRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Group-deal reads: mapper delegation, row conversion, empty-input short-circuit, My Teams paging.
 */
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
    void findBestOpenDealsReturnsEmptyWithoutQueryForNullOrEmptyIds() {
        assertThat(repository.findBestOpenDealsByProductIds(null)).isEmpty();
        assertThat(repository.findBestOpenDealsByProductIds(List.of())).isEmpty();
        verifyNoInteractions(mapper);
    }

    @Test
    void findBestOpenDealsConvertsBatchRows() {
        List<UUID> productIds = List.of(PRODUCT_ID);
        when(mapper.selectBestOpenByProductIds(productIds)).thenReturn(List.of(row()));

        var result = repository.findBestOpenDealsByProductIds(productIds);

        assertThat(result)
                .singleElement()
                .extracting(deal -> deal.productId())
                .isEqualTo(PRODUCT_ID);
        verify(mapper).selectBestOpenByProductIds(productIds);
    }

    @Test
    void pageMembershipsPassesOffsetToPageQueryAndConvertsRows() {
        UUID buyer = UUID.fromString("c0000000-0000-7000-8000-000000000002");
        when(mapper.selectMembershipPageByUserId(buyer, 5, 5L))
                .thenReturn(List.of(membershipRow("succeeded", "leader")));
        when(mapper.countMembershipsByUserId(buyer)).thenReturn(6L);

        var result = repository.pageMembershipsByUserId(new PageQuery(2, 5), buyer);

        assertThat(result.total()).isEqualTo(6);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.items())
                .singleElement()
                .satisfies(
                        membership -> {
                            assertThat(membership.dealId()).isEqualTo(DEAL_ID);
                            assertThat(membership.productId()).isEqualTo(PRODUCT_ID);
                            assertThat(membership.dealPrice()).isEqualByComparingTo("10.00");
                            assertThat(membership.currency()).isEqualTo("sgd");
                            assertThat(membership.targetSize()).isEqualTo(3);
                            assertThat(membership.currentSize()).isEqualTo(3);
                            assertThat(membership.status()).isEqualTo(DealStatus.SUCCEEDED);
                            assertThat(membership.role()).isEqualTo(MemberRole.LEADER);
                            assertThat(membership.orderId()).isNull();
                        });
    }

    @Test
    void membershipSizesDefaultToZeroAndUnknownCodesFailFast() {
        TeamMembershipDO row = membershipRow("expired", "member");
        row.setTargetSize(null);
        row.setCurrentSize(null);

        var membership = GroupDealConverter.toMembership(row);

        assertThat(membership.targetSize()).isZero();
        assertThat(membership.currentSize()).isZero();
        assertThat(membership.role()).isEqualTo(MemberRole.MEMBER);
        TeamMembershipDO unknownStatus = membershipRow("gone", "member");
        TeamMembershipDO unknownRole = membershipRow("open", "owner");
        assertThatThrownBy(() -> GroupDealConverter.toMembership(unknownStatus))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GroupDealConverter.toMembership(unknownRole))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TeamMembershipDO membershipRow(String status, String role) {
        TeamMembershipDO row = new TeamMembershipDO();
        row.setMemberId(UUID.fromString("d0000000-0000-7000-8000-000000000001"));
        row.setDealId(DEAL_ID);
        row.setProductId(PRODUCT_ID);
        row.setDealPrice(new BigDecimal("10.00"));
        row.setCurrency("sgd");
        row.setTargetSize(3);
        row.setCurrentSize(3);
        row.setStatus(status);
        row.setExpireTime(Instant.parse("2026-10-01T00:00:00Z"));
        row.setMemberRole(role);
        row.setJoinTime(Instant.parse("2026-09-01T00:00:00Z"));
        return row;
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
