package com.blocbuy.catalog.model;

import java.util.UUID;

/**
 * Terms a product carries for starting a team: how many paid members the team needs and how long it
 * stays open. Part of the Product aggregate, kept as its own value because starting a team reads
 * only these two facts and the listing itself never changes when they do.
 *
 * <p>Both are nullable in {@code biz_product}: a product that has never been configured for
 * group-buy simply cannot start a team, which {@link #configured()} answers. Configured terms are
 * necessary for START but not sufficient: START also needs an on-sale listing with stock, and a
 * team price policy in force that stays in force for the whole quote window. The bounds repeat the
 * ones documented in {@code V14__product_team_terms.sql} — the database carries no CHECK, so this
 * is where they are enforced (pgsql-ddl conventions). An administrator writes them through {@link
 * Product#configureTeamTerms}, which checks them with {@link TeamTermsSpecification}.
 */
public record ProductTeamTerms(UUID productId, Integer targetSize, Integer lifetimeSeconds) {

    /** Shortest team life the quote window fits inside; a quote is valid for 2400 seconds. */
    public static final int MIN_LIFETIME_SECONDS = 2400;

    /** Longest team life, one day. */
    public static final int MAX_LIFETIME_SECONDS = 86_400;

    /** Smallest team that is still a team. */
    public static final int MIN_TARGET_SIZE = 2;

    public ProductTeamTerms {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
    }

    /** The terms of a product that was never configured: both columns are null. */
    public static ProductTeamTerms unset(UUID productId) {
        return new ProductTeamTerms(productId, null, null);
    }

    /**
     * True when both terms are present and inside the documented bounds. This does not mean START
     * will succeed; see the class comment for what else it checks.
     */
    public boolean configured() {
        return targetSize != null
                && lifetimeSeconds != null
                && targetSize >= MIN_TARGET_SIZE
                && lifetimeSeconds >= MIN_LIFETIME_SECONDS
                && lifetimeSeconds <= MAX_LIFETIME_SECONDS;
    }
}
