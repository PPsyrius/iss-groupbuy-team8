package com.blocbuy.catalog.service;

import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import java.util.UUID;

/**
 * UC9-ext use case: configure a listing for group-buy by setting its team terms and, in the same
 * command, optionally its first-team price. The caller authenticates the administrator and passes
 * only a trusted actor id.
 */
public interface TeamTermsAppService {

    /**
     * Replace the product's team terms under its row lock and expected version; stale writes return
     * 412 without mutation. When {@code command.teamPrice()} is present its policy revision is
     * appended in the same transaction, so a rejected price also rolls the terms back.
     */
    TeamTermsResult configure(UUID productId, ConfigureTerms command, UUID actorId);

    /**
     * The command: the terms to set and, optionally, a price policy to append with them.
     *
     * @param expectedVersion the product version the administrator read
     * @param teamPrice policy to append with the terms, or null to leave the price policy alone
     */
    record ConfigureTerms(
            int expectedVersion,
            int targetSize,
            int lifetimeSeconds,
            TeamPriceAppService.ConfigurePolicy teamPrice) {}

    /**
     * The committed outcome.
     *
     * @param product the committed revision, carrying the new terms and version
     * @param teamPrice the policy appended by this command, or null when none was
     */
    record TeamTermsResult(Product product, TeamPricePolicy teamPrice) {}
}
