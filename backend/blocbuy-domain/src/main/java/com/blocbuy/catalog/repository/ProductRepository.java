package com.blocbuy.catalog.repository;

import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for the Product aggregate (one repository per aggregate root). Implemented in the
 * infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 */
public interface ProductRepository {

    /** Insert a new draft; duplicate live product numbers are reported as business conflicts. */
    void insertDraft(Product product, UUID actorId);

    /** Store one aggregate revision under its existing row lock and expected version. */
    void updateListing(Product product, int expectedVersion, UUID actorId);

    /** Soft-delete a checked draft only; reference checks and row lock belong to the caller. */
    void deleteDraft(UUID id, int expectedVersion, UUID actorId);

    /** Bounded administrator browse of all non-deleted states; null status means all. */
    PageResult<Product> pageListings(PageQuery page, ProductStatus status);

    /**
     * Serializes price issuance/configuration with product changes; caller must hold a transaction.
     */
    Optional<Product> lockById(UUID id);

    /** Active (non-deleted) product by primary key, any status. */
    Optional<Product> findById(UUID id);

    /**
     * Page of on-sale products for the buyer listing browse, newest first. {@code category} null or
     * blank means no category filter.
     */
    PageResult<Product> pageOnSale(PageQuery page, String category);
}
