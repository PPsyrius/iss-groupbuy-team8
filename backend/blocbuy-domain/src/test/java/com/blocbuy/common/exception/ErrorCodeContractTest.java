package com.blocbuy.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.identity.exception.IdentityErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Published error-code contract: every constant exposes a usable code, status and message key. */
class ErrorCodeContractTest {

    @ParameterizedTest
    @MethodSource("allCodes")
    void everyCodeExposesEnvelopeFields(ErrorCode code) {
        assertThat(code.code()).isNotBlank();
        assertThat(code.defaultMessage()).isNotBlank();
        assertThat(code.messageKey()).isNotBlank();
        assertThat(code.httpStatus()).isBetween(200, 599);
    }

    static Stream<ErrorCode> allCodes() {
        return Stream.of(
                        Stream.of(CommonErrorCode.values()),
                        Stream.of(CatalogErrorCode.values()),
                        Stream.of(GroupbuyErrorCode.values()),
                        Stream.of(IdentityErrorCode.values()))
                .flatMap(s -> s);
    }
}
