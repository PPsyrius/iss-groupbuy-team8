package com.blocbuy.catalog.model;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Product aggregate root (biz_product). Sellable catalog item; existing deal price snapshots live
 * in groupbuy and inform browse. Independent first-team price policies belong to catalog.
 *
 * <p>Rules: the aggregate root is the only entry point for mutating its state; state transitions go
 * through behavioral methods that validate the current state (no public {@code setStatus}); no
 * Lombok {@code @Data}; monetary amounts are {@code BigDecimal} major units. UC9 mutations return a
 * new aggregate revision and delegate lifecycle behavior to the concrete ProductStatus state.
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
    private final int version;
    private final ProductTeamTerms teamTerms;

    // Aggregate snapshots are constructed whole; a parameter object would only relocate the same
    // invariant checks and obscure them.
    @SuppressWarnings("java:S107")
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
            PricingMode pricingMode,
            int version,
            ProductTeamTerms teamTerms) {
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
        if (version < 0) {
            throw new IllegalArgumentException("negative product version");
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
        this.version = version;
        this.teamTerms = teamTerms == null ? ProductTeamTerms.unset(id) : teamTerms;
    }

    /** Reconstitution factory for tests; the stored revision defaults to version 0. */
    @SuppressWarnings("java:S107") // versionless mirror of the whole-snapshot factory
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
        return reconstitute(
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
                pricingMode,
                0);
    }

    /** Reconstitute the stored revision without granting callers a mutable version setter. */
    @SuppressWarnings("java:S107") // mirror of the whole-snapshot constructor
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
            PricingMode pricingMode,
            int version) {
        return reconstitute(
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
                pricingMode,
                version,
                null);
    }

    /**
     * Reconstitute the stored revision with its team terms; null terms mean the columns were never
     * configured.
     */
    @SuppressWarnings("java:S107") // mirror of the whole-snapshot constructor
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
            PricingMode pricingMode,
            int version,
            ProductTeamTerms teamTerms) {
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
                pricingMode,
                version,
                teamTerms);
    }

    /** Buyer-visible listing guard: only on-sale products may appear in the browse views. */
    public boolean isOnSale() {
        return status == ProductStatus.ON_SALE;
    }

    /**
     * Create a never-published draft; callers cannot choose lifecycle, counters or pricing mode.
     */
    public static Product draft(UUID id, String productNo, ListingDetails details) {
        if (productNo == null || !productNo.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new BizException(CatalogErrorCode.PRODUCT_INVALID);
        }
        return reconstitute(
                id,
                productNo,
                details.title(),
                details.subtitle(),
                details.description(),
                details.coverImage(),
                details.images(),
                details.category(),
                details.originalPrice(),
                details.currency(),
                details.stock(),
                0,
                ProductStatus.DRAFT,
                PricingMode.DUAL,
                0);
    }

    /** Replace editorial content, preserving identity, counters and published commercial terms. */
    public Product revise(ListingDetails details) {
        status.requireCommercialEdit(this, details);
        return reconstitute(
                id,
                productNo,
                details.title(),
                details.subtitle(),
                details.description(),
                details.coverImage(),
                details.images(),
                details.category(),
                details.originalPrice(),
                details.currency(),
                details.stock(),
                soldCount,
                status,
                pricingMode,
                nextVersion(),
                teamTerms);
    }

    /**
     * Set how many paid members a team started on this product needs and how long it stays open.
     * Allowed in every lifecycle state: the terms are read only when a team starts and frozen into
     * it, so a running team never changes. Returns the next revision; the caller has already
     * checked the version. A team price set in the same command is checked by {@code
     * TeamPricePolicy}, not here.
     *
     * @throws BizException {@code CATALOG_400_003} carrying each violated term field
     */
    public Product configureTeamTerms(int targetSize, int lifetimeSeconds) {
        Map<String, Object> violations =
                TeamTermsSpecification.violations(targetSize, lifetimeSeconds);
        if (!violations.isEmpty()) {
            throw new BizException(CatalogErrorCode.TEAM_TERMS_INVALID, violations);
        }
        return copy(status, new ProductTeamTerms(id, targetSize, lifetimeSeconds));
    }

    /** Publish or re-publish only when the current state and inventory allow it. */
    public Product publish() {
        if (stock <= 0 || originalPrice.signum() <= 0) {
            throw new BizException(CatalogErrorCode.PRODUCT_STATE_CONFLICT);
        }
        return transition(status.publish());
    }

    /** Hide a currently published listing; no quotation/deal/order snapshot is rewritten. */
    public Product delist() {
        return transition(status.delist());
    }

    /** Defend draft deletion; reference existence is a cross-aggregate application concern. */
    public void requireDeletable() {
        status.requireDeletable();
        if (soldCount != 0) {
            throw new BizException(CatalogErrorCode.PRODUCT_REFERENCED);
        }
    }

    /** Enforce client preconditions and reject version exhaustion before writing. */
    public void requireVersion(int expected) {
        if (version != expected || version == Integer.MAX_VALUE) {
            throw new BizException(CatalogErrorCode.PRODUCT_VERSION_CONFLICT);
        }
    }

    /**
     * Team terms as stored; {@link ProductTeamTerms#unset unset} for a never-configured product.
     */
    public ProductTeamTerms teamTerms() {
        return teamTerms;
    }

    /** Optimistic concurrency token; independent from append-only team-price policy versions. */
    public int version() {
        return version;
    }

    /** Copy state without exposing a generic status setter to application or controller code. */
    private Product transition(ProductStatus next) {
        return copy(next, teamTerms);
    }

    /** The next revision with a new state and terms; everything else is carried over unchanged. */
    private Product copy(ProductStatus nextStatus, ProductTeamTerms nextTerms) {
        return reconstitute(
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
                nextStatus,
                pricingMode,
                nextVersion(),
                nextTerms);
    }

    /** Never wrap the database integer concurrency token. */
    private int nextVersion() {
        requireVersion(version);
        return version + 1;
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
