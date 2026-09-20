package com.blocbuy.order.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.blocbuy.order.repository.OrderHistory;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderHistoryServiceTest {
    private final OrderHistory history = mock(OrderHistory.class);
    private final OrderHistoryService service = new OrderHistoryService(history);

    @Test
    void missingIdentityCannotReadOrderHistory() {
        assertThatThrownBy(() -> service.page(null, 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(history);
    }

    @ParameterizedTest
    @CsvSource({"0,20", "10001,20", "1,0", "1,51"})
    void paginationBoundsRejectBeforeQuery(int page, int size) {
        UUID buyer = UUID.randomUUID();
        assertThatThrownBy(() -> service.page(buyer, page, size))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(history);
    }
}
