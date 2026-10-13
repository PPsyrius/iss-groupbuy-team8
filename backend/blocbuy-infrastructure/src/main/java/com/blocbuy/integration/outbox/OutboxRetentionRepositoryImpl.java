package com.blocbuy.integration.outbox;

import com.blocbuy.common.repository.OutboxRetentionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** One short PostgreSQL transaction per retention run; the SQL skips rows locked by other work. */
@Repository
@RequiredArgsConstructor
@ConditionalOnBooleanProperty("blocbuy.events.enabled")
public class OutboxRetentionRepositoryImpl implements OutboxRetentionRepository {
    private final OutboxMapper outbox;

    @Override
    @Transactional(timeout = 3)
    public int deletePublishedBefore(Instant cutoff) {
        return outbox.deletePublishedBefore(cutoff, MAX_DELETE_ROWS);
    }
}
