package com.blocbuy.order.persistence;

import com.blocbuy.order.repository.OrderHistory;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderHistoryImpl implements OrderHistory {
    private final OrderHistoryMapper mapper;

    @Override
    public List<Entry> page(UUID buyer, int offset, int limit) {
        return mapper.page(buyer, offset, limit);
    }

    @Override
    public long count(UUID buyer) {
        return mapper.count(buyer);
    }
}
