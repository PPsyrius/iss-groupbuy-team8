package com.blocbuy.catalog.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.model.PageQuery;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UC1-ext keyword normalization: trim/collapse, blank-to-null, and neutral tokenization. */
class ListingQueryTest {

    @Test
    void nullKeywordMeansNoFilter() {
        ListingQuery query = ListingQuery.of(null, null, null, null);

        assertThat(query.keyword()).isNull();
        assertThat(query.keywordTokens()).isEmpty();
        assertThat(query.page().page()).isEqualTo(1);
    }

    @Test
    void blankKeywordAndCategoryBecomeNull() {
        ListingQuery query = ListingQuery.of("   ", "  ", 1, 20);

        assertThat(query.keyword()).isNull();
        assertThat(query.category()).isNull();
        assertThat(query.keywordTokens()).isEmpty();
    }

    @Test
    void keywordTrimsAndCollapsesWhitespace() {
        ListingQuery query = ListingQuery.of("  wireless   headphones  ", null, 1, 20);

        assertThat(query.keyword()).isEqualTo("wireless headphones");
        assertThat(query.keywordTokens()).containsExactly("wireless", "headphones");
    }

    @Test
    void tokensRemainDatabaseNeutral() {
        ListingQuery query = ListingQuery.of("HeadPhones", null, 1, 20);

        assertThat(query.keywordTokens()).containsExactly("HeadPhones");
    }

    @Test
    void likeMetacharactersRemainUnmodifiedForInfrastructure() {
        ListingQuery query = ListingQuery.of("100% cotton_\\soft", null, 1, 20);

        assertThat(query.keywordTokens()).containsExactly("100%", "cotton_\\soft");
    }

    @Test
    void overlongKeywordRejected() {
        String tooLong = "k".repeat(ListingQuery.MAX_KEYWORD_LENGTH + 1);

        assertThatThrownBy(() -> ListingQuery.of(tooLong, null, 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allTokensArePreserved() {
        String eleven = "a b c d e f g h i j k";
        ListingQuery query = ListingQuery.of(eleven, null, 1, 20);

        assertThat(query.keywordTokens())
                .containsExactly("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");
    }

    @Test
    void categoryPreservedWhenPresent() {
        ListingQuery query = ListingQuery.of("headphones", "electronics", 2, 5);

        assertThat(query.category()).isEqualTo("electronics");
        assertThat(query.page().page()).isEqualTo(2);
        assertThat(query.keywordTokens()).containsExactly("headphones");
    }

    @Test
    void singleTokenProducesSinglePattern() {
        List<String> patterns = ListingQuery.of("tea", null, null, null).keywordTokens();

        assertThat(patterns).containsExactly("tea");
    }

    @Test
    void directConstructorRejectsOverlongKeyword() {
        assertThatThrownBy(
                        () ->
                                new ListingQuery(
                                        "k".repeat(ListingQuery.MAX_KEYWORD_LENGTH + 1),
                                        null,
                                        new PageQuery(1, 20),
                                        false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void directConstructorEnforcesNormalization() {
        ListingQuery query = new ListingQuery("  a\u2003b  ", "  ", new PageQuery(1, 20), true);

        assertThat(query.keyword()).isEqualTo("a b");
        assertThat(query.keywordTokens()).containsExactly("a", "b");
        assertThat(query.category()).isNull();
        assertThat(query.aiRerank()).isTrue();
    }
}
