package com.blocbuy.catalog.model;

/** Catalog-owned policy and quote history counts, independent of current validity. */
public record PriceReferences(long quotes, long policies) {}
