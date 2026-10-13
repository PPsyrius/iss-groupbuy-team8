package com.blocbuy.common.repository;

import java.time.Instant;

/** Technical outbox maintenance port; one bounded SQL delete, never a drain-until-empty loop. */
public interface OutboxRetentionRepository {
    int MAX_DELETE_ROWS = 500;

    /**
     * Deletes at most 500 published rows older than cutoff; failed/unpublished rows are retained.
     */
    int deletePublishedBefore(Instant cutoff);
}
