package com.blocbuy.catalog.service;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductTeamTerms;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.catalog.repository.TeamPriceRepository;
import com.blocbuy.common.exception.BizException;
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

        TeamPricePolicy policy =
                teamPrices
                        .findLatestPolicy(productId)
                        .orElseThrow(
                                () -> new BizException(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE));
        requireCoversQuoteWindow(policy, now);
        // TeamPricePolicy already guarantees SGD and a positive exact-cent amount, so the only
        // thing left to check is that the team price really undercuts the individual one.
        if (policy.amount().compareTo(product.originalPrice()) >= 0) {
            throw new BizException(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
        }

        return new StartQuote(
                product.id(),
                product.title(),
                product.coverImage(),
                policy.amount(),
                policy.currency(),
                terms.targetSize(),
                terms.lifetimeSeconds());
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

    /** The revision must already be in force and still be in force when the quote lapses. */
    private static void requireCoversQuoteWindow(TeamPricePolicy policy, Instant now) {
        if (now.isBefore(policy.validFrom())
                || !now.plusSeconds(QUOTE_WINDOW_SECONDS).isBefore(policy.validUntil())) {
            throw new BizException(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
        }
    }
}
