package com.blocbuy.groupbuy.service;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.groupbuy.gateway.ListingDisplayGateway;
import com.blocbuy.groupbuy.gateway.ListingDisplayGateway.ListingDisplay;
import com.blocbuy.groupbuy.model.TeamMembership;
import com.blocbuy.groupbuy.query.MyTeamView;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link GroupDealAppService}. Reads committed team state only — status is never derived
 * from the clock, so a row always agrees with the buyer's order until the sweeper moves both.
 */
@Service
@RequiredArgsConstructor
public class GroupDealAppServiceImpl implements GroupDealAppService {

    private final GroupDealRepository groupDealRepository;
    private final ListingDisplayGateway listingDisplayGateway;

    /**
     * One page query, then one batch product lookup for the page's distinct products, then an
     * in-memory join. A product that is no longer active keeps its row with null display fields, so
     * the page and its total never disagree.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<MyTeamView> pageMyTeams(PageQuery page, UUID userId) {
        PageResult<TeamMembership> memberships =
                groupDealRepository.pageMembershipsByUserId(page, userId);
        if (memberships.items().isEmpty()) {
            return PageResult.of(List.of(), memberships.total(), page);
        }
        Set<UUID> productIds = new LinkedHashSet<>();
        memberships.items().forEach(membership -> productIds.add(membership.productId()));
        Map<UUID, ListingDisplay> listings = listingDisplayGateway.findByProductIds(productIds);
        List<MyTeamView> views =
                memberships.items().stream()
                        .map(membership -> toView(membership, listings.get(membership.productId())))
                        .toList();
        return PageResult.of(views, memberships.total(), page);
    }

    private static MyTeamView toView(TeamMembership membership, ListingDisplay listing) {
        return new MyTeamView(
                membership.dealId(),
                membership.productId(),
                listing == null ? null : listing.title(),
                listing == null ? null : listing.coverImage(),
                membership.dealPrice(),
                membership.currency(),
                membership.targetSize(),
                membership.currentSize(),
                membership.status().code(),
                membership.expireTime(),
                membership.successTime(),
                membership.role().code(),
                membership.joinTime(),
                membership.orderId());
    }
}
