package com.blocbuy.notification.persistence;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Single parameter-bound insert into the notification-owned consumed-message ledger. */
@Mapper
public interface SysConsumedMessageMapper {
    /** Returns 1 when inserted, 0 when the (event, consumer) marker already exists. */
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("consumer") String consumer);
}
