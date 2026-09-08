package com.blocbuy.catalog.repository;

import com.blocbuy.catalog.model.Product;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for the Product aggregate (one repository per aggregate root). Implemented in the
 * infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 */
public interface ProductRepository {

    /** Active (non-deleted) product by primary key, any status. */
    Optional<Product> findById(UUID id);

    /**
     * Page of on-sale products for the buyer listing browse, newest first. {@code category} null or
     * blank means no category filter.
     */
    PageResult<Product> pageOnSale(PageQuery page, String category);
}
