package com.blocbuy.groupbuy.persistence;

import com.blocbuy.groupbuy.repository.IntegrationRecipientRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter for the group audience lookup used by transactional event production. */
@Repository
@RequiredArgsConstructor
public class IntegrationRecipientRepositoryImpl implements IntegrationRecipientRepository {

    private final BizGroupDealMapper deals;

    @Override
    public List<UUID> findActiveMemberIdsByDealId(UUID dealId) {
        return deals.selectActiveMemberIdsByDealId(dealId);
    }
}
