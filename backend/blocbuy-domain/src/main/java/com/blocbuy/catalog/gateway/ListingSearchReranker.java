package com.blocbuy.catalog.gateway;

import java.util.List;
import java.util.UUID;

/** Best-effort semantic re-ranking port for a bounded listing-search result page. */
public interface ListingSearchReranker {

    /**
     * Returns candidate IDs in preferred order. Implementations fail open by returning the input
     * order when semantic ranking is disabled or unavailable.
     */
    List<UUID> rerank(String keyword, List<Candidate> candidates);

    /** Provider-neutral public listing content used only to judge search relevance. */
    record Candidate(UUID id, String title, String subtitle, String description, String category) {}
}
