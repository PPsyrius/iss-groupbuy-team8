package com.blocbuy.integration.outbox;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import com.blocbuy.common.repository.OutboxRelayRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis/PostgreSQL adapter for bounded outbox relay reads and status updates. */
@Repository
@RequiredArgsConstructor
public class OutboxRelayRepositoryImpl implements OutboxRelayRepository {

    private final OutboxMapper mapper;

    @Override
    public List<OutboxEvent> findDue(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Outbox relay limit must be between 1 and 100");
        }
        return mapper.findDue(limit);
    }

    @Override
    @Transactional
    public int recordResults(List<OutboxPublishResult> results) {
        if (results == null || results.isEmpty() || results.size() > 100) {
            throw new IllegalArgumentException("Outbox result batch must contain 1 to 100 items");
        }
        return mapper.recordResults(results);
    }
}
