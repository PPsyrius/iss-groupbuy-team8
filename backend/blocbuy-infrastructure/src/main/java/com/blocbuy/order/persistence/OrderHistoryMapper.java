package com.blocbuy.order.persistence;

import com.blocbuy.order.repository.OrderHistory;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderHistoryMapper {
    List<OrderHistory.Entry> page(
            @Param("buyer") UUID buyer, @Param("offset") int offset, @Param("limit") int limit);

    long count(@Param("buyer") UUID buyer);
}
