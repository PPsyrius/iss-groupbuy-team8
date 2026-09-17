package com.blocbuy.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.constant.ApiConstants;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Trace correlation: safe ids pass through, unsafe ids regenerate, MDC always clears. */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void acceptsSafeTraceIdAndClearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = requestWithTraceId("client.Trace-_123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilterInternal(
                request,
                response,
                (ignoredRequest, ignoredResponse) ->
                        traceIdInChain.set(MDC.get(ApiConstants.TRACE_ID_MDC_KEY)));

        assertThat(traceIdInChain).hasValue("client.Trace-_123");
        assertThat(response.getHeader(ApiConstants.TRACE_ID_HEADER)).isEqualTo("client.Trace-_123");
        assertThat(MDC.get(ApiConstants.TRACE_ID_MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidTraceIds")
    void replacesMissingOrUnsafeTraceId(String inboundTraceId) throws Exception {
        MockHttpServletRequest request = requestWithTraceId(inboundTraceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (ignoredRequest, ignoredResponse) -> {});

        assertThat(response.getHeader(ApiConstants.TRACE_ID_HEADER))
                .satisfies(value -> assertThatCodeIsUuid(value));
        assertThat(MDC.get(ApiConstants.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void clearsMdcWhenFilterChainFails() {
        MockHttpServletRequest request = requestWithTraceId("trace-on-failure");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failingChain =
                (ignoredRequest, ignoredResponse) -> {
                    throw new IllegalStateException("failure");
                };

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, failingChain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("failure");
        assertThat(MDC.get(ApiConstants.TRACE_ID_MDC_KEY)).isNull();
    }

    static Stream<String> invalidTraceIds() {
        return Stream.of(null, "   ", "x".repeat(65), "unsafe/trace");
    }

    private static MockHttpServletRequest requestWithTraceId(String traceId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (traceId != null) {
            request.addHeader(ApiConstants.TRACE_ID_HEADER, traceId);
        }
        return request;
    }

    private static void assertThatCodeIsUuid(String value) {
        assertThat(value).isNotBlank();
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }
}
