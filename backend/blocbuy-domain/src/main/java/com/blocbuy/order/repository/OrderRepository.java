package com.blocbuy.order.repository;

import com.blocbuy.order.model.Order;
import java.util.Optional;

/**
 * Repository port for the Order aggregate (one repository per aggregate root). Implemented in the
 * infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 * TODO(TEAM8-order): add intent-named methods (e.g. findById, saveIfVersion) per use case.
 */
public interface OrderRepository {

  Optional<Order> findById(String id);
}
