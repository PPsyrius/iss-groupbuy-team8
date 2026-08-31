package com.blocbuy.common.model;

import java.util.List;

/**
 * Pagination result.
 *
 * @param items page items
 * @param total total matching rows
 * @param page 1-based page number
 * @param size page size
 * @param nextCursor cursor for keyset pagination; {@code null} when offset pagination is used or no
 *     further page exists
 */
public record PageResult<T>(List<T> items, long total, int page, int size, String nextCursor) {

    public PageResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> PageResult<T> of(List<T> items, long total, PageQuery query) {
        return new PageResult<>(items, total, query.page(), query.size(), null);
    }

    public boolean hasNext() {
        return nextCursor != null || (long) page * size < total;
    }
}
