package com.blocbuy.platform;

import com.blocbuy.common.constant.ApiConstants;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Copies the current request's trace id (MDC, set by {@code TraceIdFilter}) into the {@code
 * X-Trace-Id} header of outbound service calls, so one trace id spans commerce and identity. An
 * explicit header set by the caller wins.
 */
public final class TraceIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String traceId = MDC.get(ApiConstants.TRACE_ID_MDC_KEY);
        if (traceId != null
                && !traceId.isBlank()
                && request.getHeaders().getFirst(ApiConstants.TRACE_ID_HEADER) == null) {
            request.getHeaders().set(ApiConstants.TRACE_ID_HEADER, traceId);
        }
        return execution.execute(request, body);
    }
}
