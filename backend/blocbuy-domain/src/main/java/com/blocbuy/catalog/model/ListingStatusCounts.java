package com.blocbuy.catalog.model;

/** Non-deleted listing totals, independent of pagination. */
public record ListingStatusCounts(long draft, long onSale, long offSale) {}
