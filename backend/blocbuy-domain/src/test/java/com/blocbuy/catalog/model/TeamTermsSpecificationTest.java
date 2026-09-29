package com.blocbuy.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The team terms rule set is a pure function; no Spring, clock or storage. */
class TeamTermsSpecificationTest {

    @Test
    void boundaryValuesAreSatisfied() {
        assertThat(TeamTermsSpecification.violations(2, 2400)).isEmpty();
        assertThat(TeamTermsSpecification.violations(500, 86_400)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1})
    void targetSizeBelowTwoIsRejected(int size) {
        assertThat(TeamTermsSpecification.violations(size, 2400))
                .containsOnlyKeys(TeamTermsSpecification.TARGET_SIZE);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, 0, 2399, 86_401, Integer.MAX_VALUE})
    void lifetimeOutsideTheRangeIsRejected(int lifetime) {
        assertThat(TeamTermsSpecification.violations(2, lifetime))
                .containsOnlyKeys(TeamTermsSpecification.LIFETIME_SECONDS);
    }

    /** One round trip corrects both terms, and the report order is stable. */
    @Test
    void bothViolationsAreReportedTogether() {
        Map<String, Object> violations = TeamTermsSpecification.violations(1, 10);
        assertThat(violations.keySet())
                .containsExactly(
                        TeamTermsSpecification.TARGET_SIZE,
                        TeamTermsSpecification.LIFETIME_SECONDS);
        assertThat(violations)
                .containsEntry(
                        TeamTermsSpecification.LIFETIME_SECONDS,
                        "must be between 2400 and 86400 seconds");
    }
}
