package com.blocbuy.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
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

    @Test
    void fieldErrorsAreImmutableAndCanBePrefixedWithoutChangingTheOriginal() {
        BizException ex =
                BizException.forField(
                        CommonErrorCode.VALIDATION_FAILED, "amount", "Must be positive");
        BizException nested = ex.withFieldPrefix("teamPrice.");
        assertThat(ex.details()).isEmpty();
        assertThat(ex.fieldViolations())
                .containsExactly(new BizException.FieldViolation("amount", "Must be positive"));
        assertThat(nested.errorCode()).isEqualTo(ex.errorCode());
        assertThat(nested.fieldViolations())
                .containsExactly(
                        new BizException.FieldViolation("teamPrice.amount", "Must be positive"));
        var violations = ex.fieldViolations();
        assertThatThrownBy(violations::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void prefixPreservesNonFieldDetails() {
        BizException ex =
                new BizException(CommonErrorCode.VALIDATION_FAILED, Map.of("currentVersion", 2));
        assertThat(ex.withFieldPrefix("teamPrice.")).isSameAs(ex);
        assertThat(ex.details()).containsEntry("currentVersion", 2);
        assertThat(ex.fieldViolations()).isEmpty();
    }
}
