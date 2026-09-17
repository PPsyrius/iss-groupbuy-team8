package com.blocbuy.catalog.query;

import com.blocbuy.common.model.PageQuery;
import java.util.List;
import java.util.Objects;

/**
 * Buyer search criteria: keyword plus the existing category filter and pagination.
 *
 * <p>Normalization lives here so controller, service, and tests share one rule: trim, collapse
 * inner whitespace, blank becomes {@code null} (no keyword filter). Matching itself stays in the
 * infrastructure layer; this object only carries neutral search tokens.
 *
 * <p>Semantics (owner-agreed for UC1-ext): case-insensitive match over title, subtitle, and
 * description; whitespace-separated tokens combine with AND (each token must match at least one
 * field).
 */
public record ListingQuery(String keyword, String category, PageQuery page, boolean aiRerank) {

    /** Maximum normalized keyword length; the HTTP boundary applies the same raw-input cap. */
    public static final int MAX_KEYWORD_LENGTH = 64;

    /** Validate callers that construct the query directly rather than through {@link #of}. */
    public ListingQuery {
        Objects.requireNonNull(page, "page must not be null");
        keyword = normalizeKeyword(keyword);
        category = normalizeCategory(category);
    }

    /**
     * Normalize raw request input. Blank keyword/category become {@code null} (no filter).
     *
     * @throws IllegalArgumentException when the trimmed keyword exceeds {@link #MAX_KEYWORD_LENGTH}
     */
    public static ListingQuery of(String keyword, String category, Integer page, Integer size) {
        return of(keyword, category, page, size, false);
    }

    /** Normalize raw request input and carry whether semantic re-ranking was requested. */
    public static ListingQuery of(
            String keyword, String category, Integer page, Integer size, boolean aiRerank) {
        return new ListingQuery(keyword, category, PageQuery.of(page, size), aiRerank);
    }

    /**
     * Neutral whitespace-separated tokens. Empty when there is no keyword filter; infrastructure
     * owns case-insensitive comparison and database-specific wildcard escaping.
     */
    public List<String> keywordTokens() {
        if (keyword == null) {
            return List.of();
        }
        return List.of(keyword.split(" "));
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String collapsed = keyword.replaceAll("(?U)\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return null;
        }
        if (collapsed.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "keyword must be <= " + MAX_KEYWORD_LENGTH + " characters");
        }
        return collapsed;
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return category;
    }
}
