package com.blocbuy.catalog.model;

/** All retained business history, including expired, closed and soft-deleted rows. */
public record ListingReferences(long deals, long quotes, long policies) {
    /** Any retained reference vetoes draft deletion. */
    public boolean hasAny() {
        return deals != 0 || quotes != 0 || policies != 0;
    }
}
