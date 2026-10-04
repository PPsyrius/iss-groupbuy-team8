package com.blocbuy.catalog.query;

import java.time.Instant;
import java.util.UUID;

/**
 * Active deal progress shown on catalog cards and the detail page ("how many more to form the
 * team").
 */
public record ActiveDealView(UUID dealId, int targetSize, int currentSize, Instant expireTime) {}
