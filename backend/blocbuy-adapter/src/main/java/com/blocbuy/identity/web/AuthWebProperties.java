package com.blocbuy.identity.web;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Frontend integration targets for browser flows, bound from {@code blocbuy.frontend.*}. The SPA is
 * deployed separately from this API, so the Google OAuth callback ends in a 302 back to these URLs
 * instead of a JSON body. Both values are mandatory: a missing target is a deploy-time error, so
 * startup validation fails fast rather than the first login. Non-local profiles receive values from
 * environment variables only — never commit deployment-specific hosts.
 *
 * @param successRedirectUri where the browser lands after a successful login
 * @param failureRedirectUri where the browser lands after a failed login; an optional {@code
 *     {errorCode}} placeholder is replaced with the business error code
 * @param stateCookieSecure whether the OAuth state-binding cookie carries the Secure attribute;
 *     mirrors {@code sa-token.cookie.secure} — on for HTTPS deployments, off on the plain-HTTP
 *     local/docker/ec2 profiles until the CloudFront cutover
 */
@Validated
@ConfigurationProperties(prefix = "blocbuy.frontend")
public record AuthWebProperties(
        @NotBlank String successRedirectUri,
        @NotBlank String failureRedirectUri,
        boolean stateCookieSecure) {

    /** Placeholder in {@code failureRedirectUri} swapped for the failing error code. */
    private static final String ERROR_CODE_PLACEHOLDER = "{errorCode}";

    /** Failure target with the error code applied. */
    public String failureRedirectUri(String errorCode) {
        return failureRedirectUri.replace(ERROR_CODE_PLACEHOLDER, errorCode);
    }
}
