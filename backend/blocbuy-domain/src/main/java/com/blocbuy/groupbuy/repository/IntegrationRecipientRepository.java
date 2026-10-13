package com.blocbuy.groupbuy.repository;

import java.util.List;
import java.util.UUID;

/** Read-only audience lookup used while creating a self-contained team lifecycle event. */
public interface IntegrationRecipientRepository {

    /** Active membership user IDs, in stable order, for the given deal. */
    List<UUID> findActiveMemberIdsByDealId(UUID dealId);
}
