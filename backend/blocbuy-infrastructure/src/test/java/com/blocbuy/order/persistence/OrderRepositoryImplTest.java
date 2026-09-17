package com.blocbuy.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Documents the repository skeleton contract until order persistence lands. */
class OrderRepositoryImplTest {

    private final OrderRepositoryImpl repository = new OrderRepositoryImpl();

    @Test
    void findByIdReturnsEmptyUntilPersistenceIsAvailable() {
        assertThat(repository.findById("order-1")).isEmpty();
    }
}
