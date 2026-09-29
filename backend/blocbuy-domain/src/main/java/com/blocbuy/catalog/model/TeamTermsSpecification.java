package com.blocbuy.catalog.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The rule set an administrator's team terms must satisfy, as one pure function: it collects every
 * violation so a form is corrected in one round trip, and holds no state, clock or I/O. Field keys
 * are the request's property names.
 */
final class TeamTermsSpecification {

    static final String TARGET_SIZE = "targetSize";
    static final String LIFETIME_SECONDS = "lifetimeSeconds";
    static final String TEAM_PRICE_AMOUNT = "teamPrice.amount";

    private TeamTermsSpecification() {}

    /**
     * Violations of the size and lifetime bounds and, when a team price is supplied, of the rule
     * that it must undercut the individual price. Empty means satisfied.
     *
     * @param teamPrice the team price amount in major units, or null when none is being set
     * @param originalPrice the product's individual price, in the same currency
     */
    static Map<String, Object> violations(
            int targetSize, int lifetimeSeconds, BigDecimal teamPrice, BigDecimal originalPrice) {
        Map<String, Object> violations = new LinkedHashMap<>();
        if (targetSize < ProductTeamTerms.MIN_TARGET_SIZE) {
            violations.put(TARGET_SIZE, "must be at least " + ProductTeamTerms.MIN_TARGET_SIZE);
        }
        if (lifetimeSeconds < ProductTeamTerms.MIN_LIFETIME_SECONDS
                || lifetimeSeconds > ProductTeamTerms.MAX_LIFETIME_SECONDS) {
            violations.put(
                    LIFETIME_SECONDS,
                    "must be between "
                            + ProductTeamTerms.MIN_LIFETIME_SECONDS
                            + " and "
                            + ProductTeamTerms.MAX_LIFETIME_SECONDS
                            + " seconds");
        }
        if (teamPrice != null && teamPrice.compareTo(originalPrice) >= 0) {
            violations.put(TEAM_PRICE_AMOUNT, "must be below the listing's original price");
        }
        return violations;
    }
}
