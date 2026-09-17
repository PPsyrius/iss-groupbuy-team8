package com.blocbuy.groupbuy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Accepted new-deal term validation and normalization. */
class NewDealTermsTest {
    private static final UUID ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final Instant EXPIRY = Instant.parse("2026-09-17T12:00:00.123456789Z");

    @Test
    void validTermsExposeAcceptedValuesAndNormalizePersistencePrecision() {
        NewDealTerms terms = terms(ID, "DEAL-1", PRODUCT_ID, 2, EXPIRY);

        assertThat(terms.id()).isEqualTo(ID);
        assertThat(terms.dealNo()).isEqualTo("DEAL-1");
        assertThat(terms.productId()).isEqualTo(PRODUCT_ID);
        assertThat(terms.price()).isEqualByComparingTo("10.00");
        assertThat(terms.currency()).isEqualTo("sgd");
        assertThat(terms.targetSize()).isEqualTo(2);
        assertThat(terms.expireTime()).isEqualTo(EXPIRY.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void rejectsMissingIdentifiers() {
        assertInvalid(() -> terms(null, "DEAL-1", PRODUCT_ID, 2, EXPIRY));
        assertInvalid(() -> terms(ID, "DEAL-1", null, 2, EXPIRY));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {" ", "12345678901234567890123456789012345678901234567890123456789012345"})
    void rejectsInvalidDealNumber(String dealNo) {
        assertInvalid(() -> terms(ID, dealNo, PRODUCT_ID, 2, EXPIRY));
    }

    @Test
    void rejectsInvalidSizeOrExpiry() {
        assertInvalid(() -> terms(ID, "DEAL-1", PRODUCT_ID, 1, EXPIRY));
        assertInvalid(() -> terms(ID, "DEAL-1", PRODUCT_ID, 2, null));
    }

    @Test
    void rejectsInvalidPriceOrCurrency() {
        assertInvalid(
                () ->
                        new NewDealTerms(
                                ID, "DEAL-1", PRODUCT_ID, BigDecimal.ZERO, "sgd", 2, EXPIRY));
        assertInvalid(
                () ->
                        new NewDealTerms(
                                ID,
                                "DEAL-1",
                                PRODUCT_ID,
                                new BigDecimal("10.00"),
                                "usd",
                                2,
                                EXPIRY));
    }

    private static NewDealTerms terms(
            UUID id, String dealNo, UUID productId, int targetSize, Instant expiry) {
        return new NewDealTerms(
                id, dealNo, productId, new BigDecimal("10.00"), "sgd", targetSize, expiry);
    }

    private static void assertInvalid(ThrowingCallable construction) {
        assertThatThrownBy(construction)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION);
    }
}
