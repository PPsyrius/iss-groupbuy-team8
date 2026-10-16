package com.blocbuy.platform;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.blocbuy.common.constant.ApiConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Outbound service calls carry the inbound request's trace id. */
class TraceIdPropagationInterceptorTest {

    private static final String URL = "http://identity-svc:8589/internal/users/1/active";

    private final RestClient.Builder builder =
            RestClient.builder().requestInterceptor(new TraceIdPropagationInterceptor());
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestClient client = builder.build();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void copiesTheTraceIdFromTheMdc() {
        MDC.put(ApiConstants.TRACE_ID_MDC_KEY, "trace-123");
        server.expect(requestTo(URL))
                .andExpect(header(ApiConstants.TRACE_ID_HEADER, "trace-123"))
                .andRespond(withSuccess());

        client.get().uri(URL).retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void sendsNoHeaderOutsideARequest() {
        server.expect(requestTo(URL))
                .andExpect(headerDoesNotExist(ApiConstants.TRACE_ID_HEADER))
                .andRespond(withSuccess());

        client.get().uri(URL).retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void ignoresABlankTraceId() {
        MDC.put(ApiConstants.TRACE_ID_MDC_KEY, " ");
        server.expect(requestTo(URL))
                .andExpect(headerDoesNotExist(ApiConstants.TRACE_ID_HEADER))
                .andRespond(withSuccess());

        client.get().uri(URL).retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void keepsAnExplicitHeader() {
        MDC.put(ApiConstants.TRACE_ID_MDC_KEY, "from-mdc");
        server.expect(requestTo(URL))
                .andExpect(header(ApiConstants.TRACE_ID_HEADER, "explicit"))
                .andRespond(withSuccess());

        client.get()
                .uri(URL)
                .header(ApiConstants.TRACE_ID_HEADER, "explicit")
                .retrieve()
                .toBodilessEntity();

        server.verify();
    }
}
