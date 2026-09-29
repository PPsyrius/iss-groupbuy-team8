package com.blocbuy.catalog.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The rule set an administrator's team terms must satisfy, as one pure function: it collects each
 * violated term field so the terms are corrected in one round trip, and holds no state, clock or
 * I/O. Field keys are the request's property names. The team price is not checked here: its rules
 * live only in {@code TeamPricePolicy}, so a bad price gets the same error on every endpoint.
 */
final class TeamTermsSpecification {

    static final String TARGET_SIZE = "targetSize";
    static final String LIFETIME_SECONDS = "lifetimeSeconds";

    private TeamTermsSpecification() {}

    /** Violations of the size and lifetime bounds. Empty means satisfied. */
    static Map<String, Object> violations(int targetSize, int lifetimeSeconds) {
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
        return violations;
    }
}
