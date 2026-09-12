package com.blocbuy.catalog.persistence;

import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import com.blocbuy.catalog.repository.TeamPriceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis adapter; history and quote rows have no update or delete operation. */
@Repository
@RequiredArgsConstructor
public class TeamPriceRepositoryImpl implements TeamPriceRepository {
    private final TeamPriceMapper mapper;

    /** Immutable catalog history is a deletion veto, even after expiry or soft deletion. */
    @Override
    public boolean hasProductReferences(UUID productId) {
        return mapper.existsProductReference(productId);
    }

    @Override
    public Optional<TeamPricePolicy> findLatestPolicy(UUID productId) {
        return Optional.ofNullable(
                TeamPriceConverter.toDomain(mapper.selectLatestPolicy(productId)));
    }

    @Override
    public void appendPolicy(TeamPricePolicy policy, UUID actorId, Instant now) {
        requireInserted(mapper.insertPolicy(TeamPriceConverter.toRow(policy), actorId, now));
    }

    @Override
    public void insertQuote(TeamPriceQuote quote) {
        requireInserted(mapper.insertQuote(TeamPriceConverter.toRow(quote)));
    }

    @Override
    public Optional<TeamPriceQuote> findQuoteForOwner(UUID quoteId, UUID ownerId) {
        return Optional.ofNullable(
                TeamPriceConverter.toDomain(mapper.selectQuoteForOwner(quoteId, ownerId)));
    }

    @Override
    public Instant currentTime() {
        return mapper.selectCurrentTime();
    }

    private static void requireInserted(int count) {
        if (count != 1) {
            throw new IllegalStateException("catalog insert did not affect exactly one row");
        }
    }
}
