package com.blocbuy.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The caller's network address, for per-client rate limits. Behind CloudFront the socket address is
 * the load balancer's, and {@code getRemoteAddr()} under the framework forward-headers strategy is
 * the leftmost {@code X-Forwarded-For} value, which the caller controls. So a deployment names a
 * header its edge sets itself ({@code blocbuy.web.client-address-header}, for example {@code
 * CloudFront-Viewer-Address}); without that property the socket address is used (local runs).
 *
 * <p>The address is personal data: callers pass it to the rate limiter and never log it.
 */
@Component
public class ClientAddressResolver {

    private final String header;

    public ClientAddressResolver(@Value("${blocbuy.web.client-address-header:}") String header) {
        this.header = header;
    }

    /** The client address without its port; falls back to the socket address. */
    public String resolve(HttpServletRequest request) {
        if (!header.isBlank()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return withoutPort(value.strip());
            }
        }
        return request.getRemoteAddr();
    }

    /** {@code 198.51.100.10:46532} and {@code 2001:db8::1:46532} both end in {@code :port}. */
    private static String withoutPort(String address) {
        int colon = address.lastIndexOf(':');
        return colon < 0 ? address : address.substring(0, colon);
    }
}
