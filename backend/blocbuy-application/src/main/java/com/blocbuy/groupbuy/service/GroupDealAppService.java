package com.blocbuy.groupbuy.service;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.groupbuy.query.MyTeamView;
import java.util.UUID;

/**
 * Use-case service for GroupDeal — transaction boundary sits on public command methods
 * ({@code @Transactional}, queries {@code readOnly = true}). Orchestrates aggregates, domain
 * services, repository ports, and gateways; carries no rules that belong to a single aggregate.
 */
public interface GroupDealAppService {

    /**
     * UC8 My Teams: the teams the buyer leads or joined, newest participation first. Fixed three
     * queries per page (rows, count, one batch product lookup), independent of page size, all read
     * from one snapshot so {@code total} agrees with {@code items}.
     */
    PageResult<MyTeamView> pageMyTeams(PageQuery page, UUID userId);
}
