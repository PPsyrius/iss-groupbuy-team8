package com.blocbuy.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** The client address comes from the edge header when one is configured and present. */
class ClientAddressResolverTest {

    private static final String HEADER = "CloudFront-Viewer-Address";

    private static MockHttpServletRequest request(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        if (headerValue != null) {
            request.addHeader(HEADER, headerValue);
        }
        return request;
    }

    @ParameterizedTest
    @CsvSource({
        "198.51.100.10:46532, 198.51.100.10",
        "2001:db8::1:46532, 2001:db8::1",
        "' 198.51.100.10:1 ', 198.51.100.10",
        "198.51.100.10, 198.51.100.10"
    })
    void theConfiguredHeaderIsUsedWithoutItsPort(String value, String expected) {
        assertThat(new ClientAddressResolver(HEADER).resolve(request(value))).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankHeaderFallsBackToTheSocketAddress(String value) {
        assertThat(new ClientAddressResolver(HEADER).resolve(request(value))).isEqualTo("10.0.0.5");
    }

    @Test
    void aMissingHeaderFallsBackToTheSocketAddress() {
        assertThat(new ClientAddressResolver(HEADER).resolve(request(null))).isEqualTo("10.0.0.5");
    }

    @Test
    void withoutAConfiguredHeaderTheSocketAddressIsUsed() {
        assertThat(new ClientAddressResolver("").resolve(request("198.51.100.10:46532")))
                .isEqualTo("10.0.0.5");
    }
}
