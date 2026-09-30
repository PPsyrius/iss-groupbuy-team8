package com.blocbuy.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Write protocol: partial writes fail fast as technical errors so the transaction rolls back. */
@ExtendWith(MockitoExtension.class)
class TeamPriceRepositoryImplTest {
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID ACTOR = UUID.fromString("b0000000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

    @Mock private TeamPriceMapper mapper;
    @InjectMocks private TeamPriceRepositoryImpl repository;

    /** Empty batches never generate an invalid IN clause or access the database. */
    @Test
    void emptyPolicyBatchesSkipSql() {
        assertThat(repository.findLatestPolicies(null)).isEmpty();
        assertThat(repository.findLatestPolicies(List.of())).isEmpty();
        verifyNoInteractions(mapper);
    }

    @Test
    void appendPolicyRequiresSingleRowWrite() {
        when(mapper.insertPolicy(any(), any(), any())).thenReturn(0);
        TeamPricePolicy policy =
                new TeamPricePolicy(
                        PRODUCT,
                        new BigDecimal("20"),
                        "sgd",
                        NOW.minusSeconds(10),
                        NOW.plusSeconds(3600),
                        1);

        assertThatThrownBy(() -> repository.appendPolicy(policy, ACTOR, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void insertQuoteRequiresSingleRowWrite() {
        when(mapper.insertQuote(any())).thenReturn(0);
        TeamPriceQuote quote =
                new TeamPriceQuote(
                        UUID.randomUUID(),
                        PRODUCT,
                        ACTOR,
                        1,
                        new BigDecimal("20"),
                        "sgd",
                        NOW,
                        NOW.plusSeconds(900));

        assertThatThrownBy(() -> repository.insertQuote(quote))
                .isInstanceOf(IllegalStateException.class);
    }
}
