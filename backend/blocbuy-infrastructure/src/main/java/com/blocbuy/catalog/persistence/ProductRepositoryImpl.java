package com.blocbuy.catalog.persistence;

import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.repository.ProductRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Repository implementation for Product. Placeholder until the ProductMapper + DO land with the
 * first catalog migration. Batch reads via {@code findByIds}; queries inside loops are forbidden.
 */
@Repository
public class ProductRepositoryImpl implements ProductRepository {

    @Override
    public Optional<Product> findById(String id) {
        // TODO(TEAM8-catalog): select via ProductMapper once the table exists
        return Optional.empty();
    }
}
