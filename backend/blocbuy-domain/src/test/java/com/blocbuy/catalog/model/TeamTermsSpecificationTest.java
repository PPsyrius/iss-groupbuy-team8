package com.blocbuy.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The team terms rule set is a pure function; no Spring, clock or storage. */
class TeamTermsSpecificationTest {
    private static final BigDecimal ORIGINAL = new BigDecimal("100.00");

    @Test
    void boundaryValuesAreSatisfied() {
        assertThat(TeamTermsSpecification.violations(2, 2400, null, ORIGINAL)).isEmpty();
        assertThat(
                        TeamTermsSpecification.violations(
                                500, 86_400, new BigDecimal("99.99"), ORIGINAL))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1})
    void targetSizeBelowTwoIsRejected(int size) {
        assertThat(TeamTermsSpecification.violations(size, 2400, null, ORIGINAL))
                .containsOnlyKeys(TeamTermsSpecification.TARGET_SIZE);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, 0, 2399, 86_401, Integer.MAX_VALUE})
    void lifetimeOutsideTheRangeIsRejected(int lifetime) {
        assertThat(TeamTermsSpecification.violations(2, lifetime, null, ORIGINAL))
                .containsOnlyKeys(TeamTermsSpecification.LIFETIME_SECONDS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.00", "100.01", "250"})
    void teamPriceMustUndercutTheOriginalPrice(String amount) {
        assertThat(TeamTermsSpecification.violations(2, 2400, new BigDecimal(amount), ORIGINAL))
                .containsOnlyKeys(TeamTermsSpecification.TEAM_PRICE_AMOUNT);
    }

    /** One round trip corrects the whole form, and the report order is stable. */
    @Test
    void everyViolationIsReportedTogether() {
        Map<String, Object> violations =
                TeamTermsSpecification.violations(1, 10, new BigDecimal("100.00"), ORIGINAL);
        assertThat(violations.keySet())
                .containsExactly(
                        TeamTermsSpecification.TARGET_SIZE,
                        TeamTermsSpecification.LIFETIME_SECONDS,
                        TeamTermsSpecification.TEAM_PRICE_AMOUNT);
        assertThat(violations.get(TeamTermsSpecification.LIFETIME_SECONDS))
                .isEqualTo("must be between 2400 and 86400 seconds");
    }
}
