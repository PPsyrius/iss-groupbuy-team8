package com.blocbuy.catalog.service;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.catalog.repository.TeamPriceRepository;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.identity.service.UserAppService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Product-first short transactions keep each quote coherent with one policy revision. */
@Service
@RequiredArgsConstructor
public class TeamPriceAppServiceImpl implements TeamPriceAppService {
    /** Deliberate v1 contract: at most 15 minutes, further clipped by policy validity. */
    private static final Duration QUOTE_LIFETIME = Duration.ofMinutes(15);

    private final ProductRepository products;
    private final TeamPriceRepository prices;
    private final PricingStrategyResolver strategies;
    private final UserAppService users;

    @Override
    @Transactional(timeout = 10)
    public TeamPricePolicy configure(UUID productId, UUID actorId, ConfigurePolicy command) {
        Objects.requireNonNull(actorId, "trusted actorId");
        Product product = lockProduct(productId);
        users.requireActive(actorId);
        int currentVersion =
                prices.findLatestPolicy(productId).map(TeamPricePolicy::version).orElse(0);
        if (command.expectedVersion() != currentVersion || currentVersion == Integer.MAX_VALUE) {
            throw new BizException(
                    CatalogErrorCode.TEAM_PRICE_VERSION_CONFLICT,
                    Map.of("currentVersion", currentVersion));
        }
        Instant now = prices.currentTime();
        TeamPricePolicy policy =
                new TeamPricePolicy(
                        productId,
                        command.amount(),
                        command.currency(),
                        command.validFrom(),
                        command.validUntil(),
                        currentVersion + 1);
        policy.requireCompatible(product);
        if (!now.isBefore(policy.validUntil())) {
            throw new BizException(CatalogErrorCode.TEAM_PRICE_INVALID);
        }
        prices.appendPolicy(policy, actorId, now);
        return policy;
    }

    @Override
    @Transactional(readOnly = true)
    public TeamPricePolicy getPolicy(UUID productId, UUID actorId) {
        users.requireActive(actorId);
        return prices.findLatestPolicy(productId)
                .orElseThrow(() -> new BizException(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE));
    }

    @Override
    @Transactional(timeout = 10)
    public TeamPriceQuote issueQuote(UUID productId, UUID ownerId) {
        Objects.requireNonNull(ownerId, "trusted ownerId");
        Product product = lockProduct(productId);
        users.requireActive(ownerId);
        TeamPricePolicy policy =
                prices.findLatestPolicy(productId)
                        .orElseThrow(
                                () -> new BizException(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE));
        Instant now = prices.currentTime();
        var amount =
                strategies.strategyFor(product.pricingMode()).quoteTeamPrice(product, policy, now);
        Instant maxExpiry = now.plus(QUOTE_LIFETIME);
        Instant expiry = policy.validUntil().isBefore(maxExpiry) ? policy.validUntil() : maxExpiry;
        TeamPriceQuote quote =
                new TeamPriceQuote(
                        UuidV7.next(),
                        productId,
                        ownerId,
                        policy.version(),
                        amount,
                        policy.currency(),
                        now,
                        expiry);
        prices.insertQuote(quote);
        return quote;
    }

    @Override
    @Transactional(readOnly = true)
    public TeamPriceQuote getQuote(UUID quoteId, UUID ownerId) {
        Objects.requireNonNull(ownerId, "trusted ownerId");
        users.requireActive(ownerId);
        return prices.findQuoteForOwner(quoteId, ownerId)
                .orElseThrow(() -> new BizException(CatalogErrorCode.TEAM_PRICE_QUOTE_NOT_FOUND));
    }

    private Product lockProduct(UUID productId) {
        return products.lockById(productId)
                .orElseThrow(() -> new BizException(CatalogErrorCode.PRODUCT_NOT_FOUND));
    }
}
