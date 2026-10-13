package com.blocbuy.integration.outbox;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Transaction-bound writes and bounded retention deletes for the integration outbox. */
@Mapper
public interface OutboxMapper {

    int insert(OutboxInsert row);

    int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
