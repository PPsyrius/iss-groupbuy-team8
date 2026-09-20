package com.blocbuy.order.service;

import com.blocbuy.common.model.PageResult;
import com.blocbuy.order.repository.OrderHistory;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderHistoryService {
    private final OrderHistory history;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public PageResult<OrderHistory.Entry> page(UUID buyer, int page, int size) {
        if (buyer == null || page < 1 || page > 10000 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Invalid order history page");
        }
        return new PageResult<>(
                history.page(buyer, (page - 1) * size, size),
                history.count(buyer),
                page,
                size,
                null);
    }
}
