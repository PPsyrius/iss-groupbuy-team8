package com.blocbuy.catalog.model;

import java.util.UUID;

/**
 * One audited write of a product's team terms: the terms before and after, the product revision it
 * produced, the team price policy revision appended in the same command (null when none), and who
 * did it.
 *
 * @param previous the terms before the write; unset for the first write of a product
 * @param current the terms after the write
 * @param teamPriceVersion the policy revision appended by the same command, or null
 */
public record TeamTermsChange(
        UUID productId,
        int productVersion,
        ProductTeamTerms previous,
        ProductTeamTerms current,
        Integer teamPriceVersion,
        UUID actorId) {

    public TeamTermsChange {
        if (productId == null || previous == null || current == null || actorId == null) {
            throw new IllegalArgumentException(
                    "productId, previous, current and actorId must not be null");
        }
    }
}
