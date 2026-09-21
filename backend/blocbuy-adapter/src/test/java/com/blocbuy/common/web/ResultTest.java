package com.blocbuy.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.exception.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Result envelope: success codes, error details with trace id, null error-code guard. */
class ResultTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void successFactoriesUseTheCommonSuccessCode() {
        Result<String> withData = Result.ok("payload");
        Result<Void> withoutData = Result.ok();

        assertThat(withData)
                .isEqualTo(new Result<>("COMMON_200_000", "payload", "Success", null, null));
        assertThat(withoutData)
                .isEqualTo(new Result<>("COMMON_200_000", null, "Success", null, null));
    }

    @Test
    void errorFactoriesIncludeDetailsAndCurrentTraceId() {
        MDC.put(ApiConstants.TRACE_ID_MDC_KEY, "trace-123");

        Result<Void> withDetails = Result.error(CommonErrorCode.VALIDATION_FAILED, "safe details");
        Result<Void> withoutDetails = Result.error(CommonErrorCode.BAD_REQUEST);

        assertThat(withDetails)
                .isEqualTo(
                        new Result<>(
                                "COMMON_400_002",
                                null,
                                "Request validation failed",
                                "safe details",
                                "trace-123"));
        assertThat(withoutDetails.traceId()).isEqualTo("trace-123");
        assertThat(withoutDetails.errors()).isNull();
    }

    @Test
    void errorRejectsMissingErrorCode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Result.error(null))
                .withMessage("errorCode must not be null");
    }
}
