package com.blocbuy.identity.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OIDC client settings, bound from {@code blocbuy.oauth.google.*}. Secrets arrive via
 * environment variables ({@code GOOGLE_CLIENT_ID}, {@code GOOGLE_CLIENT_SECRET}) — never committed
 * values. Missing secrets are tolerated at startup so the app boots without a Google OAuth client;
 * the gateway fails fast with a provider error when actually called unconfigured.
 *
 * @param clientId Google OAuth client id (server-side only)
 * @param clientSecret Google OAuth client secret (server-side only)
 * @param redirectUri callback URL registered in the Google console
 * @param authorizeUrl Google authorization endpoint (defaulted)
 * @param tokenUrl Google token endpoint (defaulted)
 * @param stateTtlSeconds TTL of the one-time OAuth state in seconds (defaulted)
 */
@ConfigurationProperties(prefix = "blocbuy.oauth.google")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authorizeUrl,
        String tokenUrl,
        long stateTtlSeconds) {

    /** Fixed Google endpoints; overridable only for tests. */
    public GoogleOAuthProperties {
        if (authorizeUrl == null || authorizeUrl.isBlank()) {
            authorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth";
        }
        if (tokenUrl == null || tokenUrl.isBlank()) {
            tokenUrl = "https://oauth2.googleapis.com/token";
        }
        if (stateTtlSeconds <= 0) {
            stateTtlSeconds = 300;
        }
    }

    /** True when the minimum configuration for live calls is present. */
    public boolean configured() {
        return clientId != null
                && !clientId.isBlank()
                && clientSecret != null
                && !clientSecret.isBlank()
                && redirectUri != null
                && !redirectUri.isBlank();
    }

    @Override
    public String toString() {
        return "GoogleOAuthProperties[clientId="
                + clientId
                + ", clientSecret="
                + mask(clientSecret)
                + ", redirectUri="
                + redirectUri
                + ", authorizeUrl="
                + authorizeUrl
                + ", tokenUrl="
                + tokenUrl
                + ", stateTtlSeconds="
                + stateTtlSeconds
                + "]";
    }

    private static String mask(String secret) {
        return secret == null || secret.isBlank() ? "<unset>" : "<redacted>";
    }
}
