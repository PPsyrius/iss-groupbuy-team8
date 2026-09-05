package com.blocbuy.common.exception;

/**
 * Stable, machine-readable business error code.
 *
 * <p>Rules (hard gate):
 *
 * <ul>
 *   <li>Codes are stable once published — never reused or redefined.
 *   <li>{@code httpStatus} is a plain {@code int} (not Spring's {@code HttpStatus}) because the
 *       domain layer must not depend on Spring; the adapter maps it to a response status.
 *   <li>{@code messageKey} is the i18n seam: English {@link #defaultMessage()} ships first (D15),
 *       localization resolves by key later without changing {@link #code()}.
 * </ul>
 */
public interface ErrorCode {

    /** Stable machine code, e.g. {@code "ORDER_404_001"}. Never dynamic text. */
    String code();

    /** HTTP status as int, e.g. {@code 404}. */
    int httpStatus();

    /** English default message, safe to expose to clients. */
    String defaultMessage();

    /** i18n message key, e.g. {@code "error.order.notFound"}. */
    String messageKey();
}
