package com.blocbuy.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Result envelope: null-safe items, defensive copy, next-page detection. */
class PageResultTest {

    @Test
    void nullItemsDefaultToEmpty() {
        PageResult<String> result = PageResult.of(null, 0, new PageQuery(1, 20));

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void itemsAreDefensivelyCopiedAndImmutable() {
        List<String> mutable = new ArrayList<>(List.of("a"));

        PageResult<String> result = PageResult.of(mutable, 1, new PageQuery(1, 20));
        mutable.add("b");

        assertThat(result.items()).containsExactly("a");

        List<String> items = result.items();
        assertThatThrownBy(() -> items.add("c")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hasNextDetectsFurtherPages() {
        assertThat(PageResult.of(List.of("a"), 2, new PageQuery(1, 1)).hasNext()).isTrue();
        assertThat(PageResult.of(List.of("a"), 1, new PageQuery(1, 1)).hasNext()).isFalse();
        assertThat(new PageResult<>(List.of("a"), 1, 1, 1, "cursor").hasNext()).isTrue();
    }

    @Test
    void factoryCarriesQueryPaging() {
        PageResult<String> result = PageResult.of(List.of("a", "b"), 6, new PageQuery(2, 5));

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.total()).isEqualTo(6L);
        assertThat(result.nextCursor()).isNull();
    }
}
