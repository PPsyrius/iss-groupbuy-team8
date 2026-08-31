package com.blocbuy.common.model;

/**
 * Pagination request with an enforced upper bound — unbounded full-list queries are forbidden.
 *
 * @param page 1-based page number
 * @param size page size, capped at {@link #MAX_SIZE}
 */
public record PageQuery(int page, int size) {

  public static final int MAX_SIZE = 100;
  public static final int DEFAULT_SIZE = 20;

  public PageQuery {
    if (page < 1) {
      throw new IllegalArgumentException("page must be >= 1, got " + page);
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new IllegalArgumentException("size must be in [1," + MAX_SIZE + "], got " + size);
    }
  }

  public static PageQuery of(Integer page, Integer size) {
    return new PageQuery(
        page == null ? 1 : page, size == null ? DEFAULT_SIZE : size);
  }

  /** Zero-based offset for SQL LIMIT/OFFSET; prefer keyset/cursor for deep pagination. */
  public long offset() {
    return (long) (page - 1) * size;
  }
}
