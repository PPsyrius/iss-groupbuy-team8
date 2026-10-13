package com.blocbuy.notification.persistence;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Parameter-bound, recipient-scoped queries and a single batched insert. */
@Mapper
public interface BizNotificationMapper {
    int insertIfAbsent(@Param("rows") List<BizNotificationDO> rows);

    BizNotificationDO selectById(@Param("id") UUID id);

    List<BizNotificationDO> selectPageByUserId(
            @Param("userId") UUID userId, @Param("limit") int limit, @Param("offset") long offset);

    long countByUserId(@Param("userId") UUID userId);
}
