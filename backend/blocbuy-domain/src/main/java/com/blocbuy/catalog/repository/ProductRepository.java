package com.blocbuy.catalog.repository;

import com.blocbuy.catalog.model.ListingStatusCounts;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductDisplay;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.model.TeamTermsChange;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for the Product aggregate (one repository per aggregate root). Implemented in the
 * infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 */
public interface ProductRepository {

    /**
     * Resolve an optional number under a creation lock held until the caller's transaction ends.
     */
    String resolveProductNumber(String productNo);

    /** Insert a new draft; duplicate live product numbers are reported as business conflicts. */
    void insertDraft(Product product, UUID actorId);

    /** Store one aggregate revision under its existing row lock and expected version. */
    void updateListing(Product product, int expectedVersion, UUID actorId);

    /**
     * Store the team terms of one aggregate revision under its existing row lock and expected
     * version; touches only the two terms columns, the version and the audit stamp.
     */
    void updateTeamTerms(Product product, int expectedVersion, UUID actorId);

    /** Append one immutable audit row for a team terms write, in the writer's transaction. */
    void appendTeamTermsLog(TeamTermsChange change);

    /** Soft-delete a checked draft only; reference checks and row lock belong to the caller. */
    void deleteDraft(UUID id, int expectedVersion, UUID actorId);

    /** Bounded administrator browse of all non-deleted states; null status means all. */
    PageResult<Product> pageListings(PageQuery page, ProductStatus status);

    /** All three status totals in one query, using the administrator page's deletion filter. */
    ListingStatusCounts countListingStatuses();

    /**
     * Serializes price issuance/configuration with product changes; caller must hold a transaction.
     */
    Optional<Product> lockById(UUID id);

    /** Active (non-deleted) product by primary key, any status. */
    Optional<Product> findById(UUID id);

    /**
     * Display fields of active (non-deleted) products by primary key, any status, in one batch
     * query (never per id — N+1 is forbidden). Reads only id, title and cover image, so a row whose
     * other columns would fail the aggregate's invariants still displays. Unknown ids are absent
     * from the result; empty input returns an empty list.
     */
    List<ProductDisplay> findDisplaysByIds(Collection<UUID> ids);

    /**
     * Takes one unit off an on-sale product and counts the sale, as a single conditional update —
     * never read-then-write. Called inside the transaction that settles a verified payment.
     *
     * @return false when the product is gone, not on sale, or out of stock; the caller decides
     *     whether that is a business rejection or a rollback
     */
    boolean consumeOneUnit(UUID id);

    /**
     * Page of on-sale products for the buyer listing browse, newest first. {@code category} null or
     * blank means no category filter.
     */
    PageResult<Product> pageOnSale(PageQuery page, String category);

    /**
     * Page of on-sale products matching keyword plus category, newest first. {@code category} null
     * or blank means no category filter; {@code keywordTokens} null or empty means no keyword
     * filter. Tokens combine with AND and fields with OR in the infrastructure query.
     */
    PageResult<Product> searchOnSale(PageQuery page, String category, List<String> keywordTokens);
}
