package com.blocbuy.catalog.persistence;

import com.blocbuy.catalog.model.PriceReferences;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import com.blocbuy.catalog.repository.TeamPriceRepository;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    public PriceReferences countProductReferences(UUID productId) {
        var counts = mapper.countProductReferences(productId);
        return new PriceReferences(counts.getQuotes(), counts.getPolicies());
    }

    @Override
    public Optional<TeamPricePolicy> findLatestPolicy(UUID productId) {
        return Optional.ofNullable(
                TeamPriceConverter.toDomain(mapper.selectLatestPolicy(productId)));
    }

    /** Count and data use the same predicate and the application's read snapshot. */
    @Override
    public PageResult<TeamPricePolicy> pagePolicyRevisions(UUID productId, PageQuery page) {
        long total = mapper.countPolicyRevisions(productId);
        var items =
                mapper.selectPolicyRevisions(productId, page.size(), page.offset()).stream()
                        .map(TeamPriceConverter::toDomain)
                        .toList();
        return PageResult.of(items, total, page);
    }

    @Override
    public Map<UUID, TeamPricePolicy> findLatestPolicies(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectLatestPolicies(productIds).stream()
                .map(TeamPriceConverter::toDomain)
                .collect(Collectors.toMap(TeamPricePolicy::productId, Function.identity()));
    }

    @Override
    public long countListingsWithActivePolicy() {
        return mapper.countListingsWithActivePolicy();
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
