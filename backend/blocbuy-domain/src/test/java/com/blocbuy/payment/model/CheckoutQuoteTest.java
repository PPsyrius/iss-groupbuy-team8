package com.blocbuy.payment.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Immutable server-owned price commitment and its validation. */
class CheckoutQuoteTest {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Instant DEADLINE = Instant.parse("2026-09-20T12:00:00Z");

    private CheckoutQuote quote(
            String operation, String amount, String currency, Instant validity, Instant deadline) {
        return new CheckoutQuote(
                ID, ID, operation, ID, new BigDecimal(amount), currency, validity, deadline);
    }

    @Test
    void maximumDatabaseAmountConvertsExactlyToProviderMinorUnits() {
        var quote = quote("START", "9999999999999999.99", "sgd", DEADLINE, DEADLINE);
        assertThat(quote.amount().movePointRight(2).longValueExact())
                .isEqualTo(999999999999999999L);
        assertThat(quote("JOIN", "0.01", "sgd", DEADLINE, DEADLINE).amount())
                .isEqualByComparingTo("0.01");
    }

    @ParameterizedTest
    @CsvSource({
        "START,0,sgd",
        "START,-1,sgd",
        "START,10000000000000000,sgd",
        "START,0.001,sgd",
        "OTHER,1,sgd",
        "START,1,usd"
    })
    void rejectsInvalidMoneyCurrencyAndOperation(String operation, String amount, String currency) {
        assertThatThrownBy(() -> quote(operation, amount, currency, DEADLINE, DEADLINE))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void eitherDeadlineIncludingExactBoundaryExpiresQuote() {
        var quoteFirst = quote("START", "1", "sgd", DEADLINE, DEADLINE.plusSeconds(1));
        var businessFirst = quote("START", "1", "sgd", DEADLINE.plusSeconds(1), DEADLINE);
        assertThat(quoteFirst.expiredAt(DEADLINE.minusNanos(1))).isFalse();
        assertThat(quoteFirst.expiredAt(DEADLINE)).isTrue();
        assertThat(businessFirst.expiredAt(DEADLINE)).isTrue();
        assertThat(businessFirst.expiredAt(DEADLINE.plusSeconds(2))).isTrue();
    }
}
