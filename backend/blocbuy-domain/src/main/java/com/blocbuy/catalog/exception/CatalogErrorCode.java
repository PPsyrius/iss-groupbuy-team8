package com.blocbuy.catalog.exception;

import com.blocbuy.common.exception.ErrorCode;

/**
 * Catalog domain error codes. Once published, codes are never reused or redefined; messages may be
 * localized via {@link #messageKey()}, codes may not.
 */
public enum CatalogErrorCode implements ErrorCode {
  CATALOG_PLACEHOLDER("CATALOG_404_001", 404, "Product not found", "error.catalog.productNotFound");

  private final String code;
  private final int httpStatus;
  private final String defaultMessage;
  private final String messageKey;

  CatalogErrorCode(String code, int httpStatus, String defaultMessage, String messageKey) {
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
