package com.blocbuy.common.repository;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.util.List;

/** Persistence port for bounded outbox relay batches. */
public interface OutboxRelayRepository {

    List<OutboxEvent> findDue(int limit);

    int recordResults(List<OutboxPublishResult> results);
}
