package com.blocbuy.catalog.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.model.PageQuery;
import org.junit.jupiter.api.Test;

/** UC1-ext keyword normalization: trim/collapse, blank-to-null, and neutral tokenization. */
class ListingQueryTest {

    /** Verifies that a null keyword disables filtering and keeps default pagination. */
    @Test
    void nullKeywordMeansNoFilter() {
        ListingQuery query = ListingQuery.of(null, null, null, null);

        assertThat(query.keyword()).isNull();
        assertThat(query.keywordTokens()).isEmpty();
        assertThat(query.page().page()).isEqualTo(1);
    }

    /** Verifies that blank keyword and category inputs normalize to no filters. */
    @Test
    void blankKeywordAndCategoryBecomeNull() {
        ListingQuery query = ListingQuery.of("   ", "  ", 1, 20);

        assertThat(query.keyword()).isNull();
        assertThat(query.category()).isNull();
        assertThat(query.keywordTokens()).isEmpty();
    }

    /** Verifies trimming and collapsing of repeated keyword whitespace. */
    @Test
    void keywordTrimsAndCollapsesWhitespace() {
        ListingQuery query = ListingQuery.of("  wireless   headphones  ", null, 1, 20);

        assertThat(query.keyword()).isEqualTo("wireless headphones");
        assertThat(query.keywordTokens()).containsExactly("wireless", "headphones");
    }

    /** Verifies that token casing remains available for database-side comparison. */
    @Test
    void tokensRemainDatabaseNeutral() {
        ListingQuery query = ListingQuery.of("HeadPhones", null, 1, 20);

        assertThat(query.keywordTokens()).containsExactly("HeadPhones");
    }

    /** Verifies that SQL wildcard characters remain untouched at the query boundary. */
    @Test
    void likeMetacharactersRemainUnmodifiedForInfrastructure() {
        ListingQuery query = ListingQuery.of("100% cotton_\\soft", null, 1, 20);

        assertThat(query.keywordTokens()).containsExactly("100%", "cotton_\\soft");
    }

    /** Verifies rejection of a normalized keyword beyond the public length limit. */
    @Test
    void overlongKeywordRejected() {
        String tooLong = "k".repeat(ListingQuery.MAX_KEYWORD_LENGTH + 1);

        assertThatThrownBy(() -> ListingQuery.of(tooLong, null, 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Verifies that tokenization does not silently discard later terms. */
    @Test
    void allTokensArePreserved() {
        String eleven = "a b c d e f g h i j k";
        ListingQuery query = ListingQuery.of(eleven, null, 1, 20);

        assertThat(query.keywordTokens())
                .containsExactly("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");
    }

    /** Verifies preservation of an explicit category and page selection. */
    @Test
    void categoryPreservedWhenPresent() {
        ListingQuery query = ListingQuery.of("headphones", "electronics", 2, 5);

        assertThat(query.category()).isEqualTo("electronics");
        assertThat(query.page().page()).isEqualTo(2);
        assertThat(query.keywordTokens()).containsExactly("headphones");
    }

    /** Verifies that direct construction enforces the same keyword length invariant. */
    @Test
    void directConstructorRejectsOverlongKeyword() {
        String tooLong = "k".repeat(ListingQuery.MAX_KEYWORD_LENGTH + 1);
        PageQuery page = new PageQuery(1, 20);

        assertThatThrownBy(() -> new ListingQuery(tooLong, null, page, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Verifies that direct construction normalizes Unicode whitespace and blank categories. */
    @Test
    void directConstructorEnforcesNormalization() {
        ListingQuery query = new ListingQuery("  a\u2003b  ", "  ", new PageQuery(1, 20), true);

        assertThat(query.keyword()).isEqualTo("a b");
        assertThat(query.keywordTokens()).containsExactly("a", "b");
        assertThat(query.category()).isNull();
        assertThat(query.aiRerank()).isTrue();
    }
}
