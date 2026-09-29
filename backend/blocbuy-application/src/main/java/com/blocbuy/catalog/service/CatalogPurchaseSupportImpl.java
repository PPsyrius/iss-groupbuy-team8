package com.blocbuy.catalog.service;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductTeamTerms;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.catalog.repository.TeamPriceRepository;
import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Catalog side of a purchase. Reads only catalog's own aggregates through its own ports, and hands
 * the order module a validated snapshot — no product row, price policy or DO leaves this class.
 */
@Service
@RequiredArgsConstructor
public class CatalogPurchaseSupportImpl implements CatalogPurchaseSupport {

    /**
     * A quote stays valid for this long, so a price revision must cover the whole window: a policy
     * expiring inside it could otherwise price a checkout the buyer completes after it lapsed.
     */
    private static final long QUOTE_WINDOW_SECONDS = 2400;

    private static final String SUPPORTED_CURRENCY = "sgd";

    private final ProductRepository products;
    private final TeamPriceRepository teamPrices;

    @Override
    public StartQuote lockStartQuote(UUID productId, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        Startable startable = lockStartable(productId);

        TeamPricePolicy policy =
                teamPrices
                        .findLatestPolicy(productId)
                        .orElseThrow(
                                () -> new BizException(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE));
        requireCoversQuoteWindow(policy, now);
        // TeamPricePolicy already guarantees SGD and a positive exact-cent amount, so the only
        // thing left to check is that the team price really undercuts the individual one.
        requireUndercuts(policy.amount(), startable.product());
        return startable.priced(policy.amount(), policy.currency());
    }

    @Override
    public StartQuote lockQuotedStart(UUID productId, UUID quoteId, UUID buyerId) {
        if (quoteId == null || buyerId == null) {
            throw new IllegalArgumentException("quoteId and buyerId must not be null");
        }
        Startable startable = lockStartable(productId);
        // Read after the lock: a buyer who waited on it must not carry a quote that lapsed
        // meanwhile through on an earlier clock.
        Instant now = teamPrices.currentTime();

        // Missing, someone else's and another product's quote look the same, so a quote id is
        // never an existence oracle.
        TeamPriceQuote quote =
                teamPrices
                        .findQuoteForOwner(quoteId, buyerId)
                        .filter(found -> found.productId().equals(productId))
                        .orElseThrow(
                                () ->
                                        new BizException(
                                                CatalogErrorCode.TEAM_PRICE_QUOTE_NOT_FOUND));
        if (!now.isBefore(quote.expiresAt())) {
            throw new BizException(CatalogErrorCode.TEAM_PRICE_QUOTE_EXPIRED);
        }
        // The latest policy is deliberately not read: honouring the shown price is the point. The
        // product's own price may have moved, though, and a team price must stay a discount.
        requireUndercuts(quote.amount(), startable.product());
        return startable.priced(quote.amount(), quote.currency());
    }

    @Override
    public PurchasableProduct requirePurchasable(UUID productId) {
        Product product =
                products.findById(productId)
                        .orElseThrow(() -> new BizException(CatalogErrorCode.PRODUCT_NOT_FOUND));
        requireSellable(product);
        return new PurchasableProduct(
                product.id(), product.title(), product.coverImage(), product.currency());
    }

    @Override
    public boolean consumeOneUnit(UUID productId) {
        return products.consumeOneUnit(productId);
    }

    private static void requireSellable(Product product) {
        if (!product.isOnSale()
                || product.stock() < 1
                || !SUPPORTED_CURRENCY.equals(product.currency())) {
            throw new BizException(CatalogErrorCode.PRODUCT_NOT_PURCHASABLE);
        }
    }

    /** Locks the product and proves a team can be started on it, before any price is read. */
    private Startable lockStartable(UUID productId) {
        Product product =
                products.lockById(productId)
                        .orElseThrow(() -> new BizException(CatalogErrorCode.PRODUCT_NOT_FOUND));
        requireSellable(product);

        ProductTeamTerms terms =
                products.findTeamTerms(productId)
                        .orElseThrow(() -> new BizException(CatalogErrorCode.PRODUCT_NOT_FOUND));
        if (!terms.startable()) {
            throw new BizException(CatalogErrorCode.PRODUCT_NOT_STARTABLE);
        }
        return new Startable(product, terms);
    }

    private static void requireUndercuts(BigDecimal teamPrice, Product product) {
        if (teamPrice.compareTo(product.originalPrice()) >= 0) {
            throw new BizException(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
        }
    }

    /** The revision must already be in force and still be in force when the quote lapses. */
    private static void requireCoversQuoteWindow(TeamPricePolicy policy, Instant now) {
        if (now.isBefore(policy.validFrom())
                || !now.plusSeconds(QUOTE_WINDOW_SECONDS).isBefore(policy.validUntil())) {
            throw new BizException(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
        }
    }

    /** A locked product that may start a team, waiting only for its price. */
    private record Startable(Product product, ProductTeamTerms terms) {
        StartQuote priced(BigDecimal amount, String currency) {
            return new StartQuote(
                    product.id(),
                    product.title(),
                    product.coverImage(),
                    amount,
                    currency,
                    terms.targetSize(),
                    terms.lifetimeSeconds());
        }
    }
}
