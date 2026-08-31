package com.blocbuy.payment.exception;

import com.blocbuy.common.exception.ErrorCode;

/**
 * Payment domain error codes. Once published, codes are never reused or redefined; messages may
 * be localized via {@link #messageKey()}, codes may not. Provider-side details (Stripe decline
 * codes, raw API responses) are never exposed to clients — they map to these stable codes.
 */
public enum PaymentErrorCode implements ErrorCode {
  PAYMENT_NOT_FOUND("PAYMENT_404_001", 404, "Payment not found", "error.payment.notFound"),
  PAYMENT_STATE_INVALID(
      "PAYMENT_409_001",
      409,
      "Payment is in a state that does not allow this operation",
      "error.payment.stateInvalid"),
  PAYMENT_WEBHOOK_SIGNATURE_INVALID(
      "PAYMENT_400_001",
      400,
      "Webhook signature verification failed",
      "error.payment.webhookSignatureInvalid"),
  PAYMENT_PROVIDER_ERROR(
      "PAYMENT_502_001",
      502,
      "Payment provider request failed",
      "error.payment.providerError");

  private final String code;
  private final int httpStatus;
  private final String defaultMessage;
  private final String messageKey;

  PaymentErrorCode(String code, int httpStatus, String defaultMessage, String messageKey) {
    this.code = code;
    this.httpStatus = httpStatus;
    this.defaultMessage = defaultMessage;
    this.messageKey = messageKey;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public int httpStatus() {
    return httpStatus;
  }

  @Override
  public String defaultMessage() {
    return defaultMessage;
  }

  @Override
  public String messageKey() {
    return messageKey;
  }
}
