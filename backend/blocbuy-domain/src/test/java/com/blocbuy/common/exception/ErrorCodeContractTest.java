package com.blocbuy.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.exception.ParticipationErrorCode;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.notification.exception.NotificationErrorCode;
import com.blocbuy.order.exception.OrderErrorCode;
import com.blocbuy.payment.exception.PaymentErrorCode;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Published error-code contract: every constant exposes a usable code, status and message key. */
class ErrorCodeContractTest {

    @ParameterizedTest
    @MethodSource("allCodes")
    void everyCodeExposesEnvelopeFields(ErrorCode code) {
        assertThat(code.code()).matches("[A-Z]+_[0-9]{3}_[0-9]{3}");
        assertThat(code.code().split("_")[1]).isEqualTo(String.valueOf(code.httpStatus()));
        assertThat(code.defaultMessage()).isNotBlank();
        assertThat(code.messageKey()).isNotBlank().doesNotContainAnyWhitespaces();
        assertThat(code.httpStatus()).isBetween(200, 599);
    }

    @Test
    void publishedCodesAndMessageKeysAreUnique() {
        List<ErrorCode> codes = allCodes().toList();

        assertThat(codes).extracting(ErrorCode::code).doesNotHaveDuplicates();
        assertThat(codes).extracting(ErrorCode::messageKey).doesNotHaveDuplicates();
    }

    static Stream<ErrorCode> allCodes() {
        return Stream.of(
                        Stream.of(CommonErrorCode.values()),
                        Stream.of(CatalogErrorCode.values()),
                        Stream.of(GroupbuyErrorCode.values()),
                        Stream.of(IdentityErrorCode.values()),
                        Stream.of(NotificationErrorCode.values()),
                        Stream.of(OrderErrorCode.values()),
                        Stream.of(PaymentErrorCode.values()),
                        Stream.of(ParticipationErrorCode.values()))
                .flatMap(s -> s);
    }
}
