package com.blocbuy.bootstrap.config;

import com.blocbuy.common.constant.ApiConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * End-to-end trace correlation: every request carries a traceId. Inbound {@code X-Trace-Id} is
 * honored (bounded length, sanitized); otherwise one is generated. The id is placed in MDC for all
 * downstream logging and echoed back in the response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  private static final int MAX_TRACE_ID_LENGTH = 64;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String traceId = sanitize(request.getHeader(ApiConstants.TRACE_ID_HEADER));
    if (traceId == null) {
      traceId = UUID.randomUUID().toString();
    }
    MDC.put(ApiConstants.TRACE_ID_MDC_KEY, traceId);
    response.setHeader(ApiConstants.TRACE_ID_HEADER, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(ApiConstants.TRACE_ID_MDC_KEY);
    }
  }

  private static String sanitize(String value) {
    if (value == null || value.isBlank() || value.length() > MAX_TRACE_ID_LENGTH) {
      return null;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '-' && c != '_' && c != '.') {
        return null;
      }
    }
    return value;
  }
}
