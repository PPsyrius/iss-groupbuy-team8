package com.blocbuy.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutPaymentRepositoryImplTest {
    private final PaymentMapper mapper = mock(PaymentMapper.class);
    private final CheckoutPaymentRepositoryImpl repository =
            new CheckoutPaymentRepositoryImpl(mapper);

    @Test
    void missingRowsHaveNoOrderOrDealAndCannotBeLocked() {
        UUID id = UUID.randomUUID();
        assertThat(repository.orderId(id)).isNull();
        assertThat(repository.fulfilledDealId(id)).isNull();
        assertThat(repository.findById(id)).isEmpty();
        assertThatThrownBy(() -> repository.lock(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void scanAndReviewPageBoundsAreCheckedBeforeDatabaseAccess() {
        for (int limit : new int[] {0, 101}) {
            assertThatThrownBy(() -> repository.pendingBefore(Instant.now(), limit))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repository.reviewCases(null, limit))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void zeroUpdatedRowsCauseRetryableTransactionFailure() {
        assertThatThrownBy(() -> repository.finishEvent("evt_fixture", UUID.randomUUID(), "DONE"))
                .isInstanceOf(IllegalStateException.class);
    }
}
