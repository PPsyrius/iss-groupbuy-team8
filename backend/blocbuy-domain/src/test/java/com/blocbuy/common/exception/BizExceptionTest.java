package com.blocbuy.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Rejection construction: absent details default to empty, absent codes fail fast. */
class BizExceptionTest {

    @Test
    void nullDetailsDefaultToEmpty() {
        assertThat(new BizException(CommonErrorCode.VALIDATION_FAILED, null).details()).isEmpty();
    }

    @Test
    void nullErrorCodeFailsFast() {
        assertThatThrownBy(() -> new BizException(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
