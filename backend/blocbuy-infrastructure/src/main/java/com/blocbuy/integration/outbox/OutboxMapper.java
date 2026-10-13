package com.blocbuy.integration.outbox;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Transaction-bound writes and bounded retention deletes for the integration outbox. */
@Mapper
public interface OutboxMapper {

    int insert(OutboxInsert row);

    int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    List<OutboxEvent> findDue(@Param("limit") int limit);

    int recordResults(
            @Param("results") List<OutboxPublishResult> results,
            @Param("maxAttempts") int maxAttempts);
}
