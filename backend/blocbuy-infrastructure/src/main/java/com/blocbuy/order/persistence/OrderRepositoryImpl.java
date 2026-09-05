package com.blocbuy.order.persistence;

import com.blocbuy.order.model.Order;
import com.blocbuy.order.repository.OrderRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Repository implementation for Order. Placeholder until the OrderMapper + DO land with the first
 * order migration. Batch reads via {@code findByIds}; queries inside loops are forbidden.
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    @Override
    public Optional<Order> findById(String id) {
        // TODO(TEAM8-order): select via OrderMapper once the table exists
        return Optional.empty();
    }
}
