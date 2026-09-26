package com.blocbuy.catalog.model;

import java.util.UUID;

/**
 * The display fields of one product, read without rebuilding the {@link Product} aggregate. A
 * reader that needs only a title and a cover — My Teams — must not fail because some unrelated
 * column of the row (images, status, price) would not pass the aggregate's invariants.
 *
 * @param productId product identity
 * @param title current listing title
 * @param coverImage current cover image URL; may be null
 */
public record ProductDisplay(UUID productId, String title, String coverImage) {}
