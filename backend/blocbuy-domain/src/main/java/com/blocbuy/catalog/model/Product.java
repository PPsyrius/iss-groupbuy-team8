package com.blocbuy.catalog.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Product aggregate root (biz_product). Sellable catalog item; group-deal pricing lives on the
 * groupbuy side and reaches catalog only as a pricing input.
 *
 * <p>Rules: the aggregate root is the only entry point for mutating its state; state transitions go
 * through behavioral methods that validate the current state (no public {@code setStatus}); no
 * Lombok {@code @Data}; monetary amounts are {@code BigDecimal} major units. The browse use case is
 * read-only, so only reconstitution exists so far — mutation behavior lands with the first write
 * use case.
 */
public final class Product {

    private final UUID id;
    private final String productNo;
    private final String title;
    private final String subtitle;
    private final String description;
    private final String coverImage;
    private final List<String> images;
    private final String category;
    private final BigDecimal originalPrice;
    private final String currency;
    private final int stock;
    private final int soldCount;
    private final ProductStatus status;
    private final PricingMode pricingMode;

    private Product(
            UUID id,
            String productNo,
            String title,
            String subtitle,
            String description,
            String coverImage,
            List<String> images,
            String category,
            BigDecimal originalPrice,
            String currency,
            int stock,
            int soldCount,
            ProductStatus status,
            PricingMode pricingMode) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (originalPrice == null || originalPrice.signum() < 0) {
            throw new IllegalArgumentException("originalPrice must not be null or negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (pricingMode == null) {
            throw new IllegalArgumentException("pricingMode must not be null");
        }
        this.id = id;
        this.productNo = productNo;
        this.title = title;
        this.subtitle = subtitle;
        this.description = description;
        this.coverImage = coverImage;
        this.images = images == null ? List.of() : List.copyOf(images);
        this.category = category;
        this.originalPrice = originalPrice;
        this.currency = currency;
        this.stock = stock;
        this.soldCount = soldCount;
        this.status = status;
        this.pricingMode = pricingMode;
    }

    /** Reconstitution factory for the infrastructure converter; all fields come from the row. */
    public static Product reconstitute(
            UUID id,
            String productNo,
            String title,
            String subtitle,
            String description,
            String coverImage,
            List<String> images,
            String category,
            BigDecimal originalPrice,
            String currency,
            int stock,
            int soldCount,
            ProductStatus status,
            PricingMode pricingMode) {
        return new Product(
                id,
                productNo,
                title,
                subtitle,
                description,
                coverImage,
                images,
                category,
                originalPrice,
                currency,
                stock,
                soldCount,
                status,
                pricingMode);
    }

    /** Buyer-visible listing guard: only on-sale products may appear in the browse views. */
    public boolean isOnSale() {
        return status == ProductStatus.ON_SALE;
    }

    public UUID id() {
        return id;
    }

    public String productNo() {
        return productNo;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public String description() {
        return description;
    }

    public String coverImage() {
        return coverImage;
    }

    /** Ordered image URLs beyond the cover; never filtered on. */
    public List<String> images() {
        return images;
    }

    public String category() {
        return category;
    }

    /** List price in major units; group pricing comes from the active deal. */
    public BigDecimal originalPrice() {
        return originalPrice;
    }

    public String currency() {
        return currency;
    }

    public int stock() {
        return stock;
    }

    public int soldCount() {
        return soldCount;
    }

    public ProductStatus status() {
        return status;
    }

    /** Selects the pricing strategy used to resolve this listing's displayed prices. */
    public PricingMode pricingMode() {
        return pricingMode;
    }
}
