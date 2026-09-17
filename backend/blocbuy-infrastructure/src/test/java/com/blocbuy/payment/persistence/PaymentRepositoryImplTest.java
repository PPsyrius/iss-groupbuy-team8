package com.blocbuy.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Documents the repository skeleton contract until payment persistence lands. */
class PaymentRepositoryImplTest {

    @Test
    void findByIdReturnsEmptyWhilePersistenceIsPlaceholder() {
        PaymentRepositoryImpl repository = new PaymentRepositoryImpl();

        assertThat(repository.findById("payment-123")).isEmpty();
    }
}
