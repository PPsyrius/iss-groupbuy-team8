package com.blocbuy.catalog.repository;

import com.blocbuy.catalog.model.Product;
import java.util.Optional;

/**
 * Repository port for the Product aggregate (one repository per aggregate root). Implemented in the
 * infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 * TODO(TEAM8-catalog): add intent-named methods (e.g. findById, saveIfVersion) per use case.
 */
public interface ProductRepository {

  Optional<Product> findById(String id);
}
