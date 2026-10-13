package com.blocbuy.integration.outbox;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import com.blocbuy.common.repository.OutboxRelayRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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

    /** One statement, atomic on its own; the relay holds no transaction across provider I/O. */
    @Override
    public int recordResults(List<OutboxPublishResult> results, int maxAttempts) {
        if (results == null || results.isEmpty() || results.size() > 100) {
            throw new IllegalArgumentException("Outbox result batch must contain 1 to 100 items");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Outbox relay maxAttempts must be positive");
        }
        return mapper.recordResults(results, maxAttempts);
    }
}
