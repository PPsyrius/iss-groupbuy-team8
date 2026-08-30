package com.blocbuy.common.constant;

/** API-wide constants shared by adapter and consumers of the contract. */
public final class ApiConstants {

  /** Base path for all v1 resources (D11). */
  public static final String API_V1 = "/api/v1";

  /** Header carrying the client-supplied idempotency key for write endpoints. */
  public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  /** Header carrying the end-to-end trace id; generated server-side when absent. */
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  /** SLF4J MDC key for the trace id. */
  public static final String TRACE_ID_MDC_KEY = "traceId";

  private ApiConstants() {}
}
