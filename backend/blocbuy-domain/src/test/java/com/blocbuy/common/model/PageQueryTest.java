package com.blocbuy.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pagination bounds: 1-based pages, capped sizes, null-tolerant factory, zero-based offsets. */
class PageQueryTest {

    @Test
    void nullFactoryArgsFallBackToDefaults() {
        PageQuery query = PageQuery.of(null, null);

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(PageQuery.DEFAULT_SIZE);
    }

    @Test
    void explicitFactoryArgsArePreserved() {
        PageQuery query = PageQuery.of(2, 5);

        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(5);
    }

    @Test
    void rejectsOutOfRangePageAndSize() {
        assertThatThrownBy(() -> new PageQuery(0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(-1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(1, PageQuery.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxSizeBoundaryIsAccepted() {
        assertThat(new PageQuery(1, PageQuery.MAX_SIZE).size()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    void offsetIsZeroBased() {
        assertThat(new PageQuery(1, 20).offset()).isZero();
        assertThat(new PageQuery(2, 5).offset()).isEqualTo(5L);
        assertThat(new PageQuery(3, 20).offset()).isEqualTo(40L);
    }
}
