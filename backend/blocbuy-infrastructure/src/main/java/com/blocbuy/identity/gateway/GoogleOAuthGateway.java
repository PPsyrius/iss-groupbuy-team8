package com.blocbuy.identity.gateway;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.model.OAuthProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google implementation of the {@link OAuthIdentityGateway} port. This is the only class in the
 * codebase allowed to reference {@code com.google.api}/{@code com.google.gson} client types.
 *
 * <p>Flow: exchange the authorization code at the token endpoint (bounded timeouts), then verify
 * the id_token signature + iss/aud/exp via Google's certs and require a verified email. Provider
 * access/refresh tokens are used in-memory for this call only — never stored, never logged, never
 * seen by Sa-Token. Provider failures are logged once here (no PII) and surface as {@link
 * IdentityErrorCode#OAUTH_PROVIDER_ERROR}; verification failures as {@link
 * IdentityErrorCode#OAUTH_TOKEN_INVALID}.
 */
@Slf4j
@Component
@EnableConfigurationProperties(GoogleOAuthProperties.class)
public class GoogleOAuthGateway implements OAuthIdentityGateway {

    private static final String SCOPE = "openid email profile";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final GoogleOAuthProperties properties;
    private final RestClient restClient;
    private final GoogleIdTokenVerifier verifier;

    /** Spring entry point; {@code @Autowired} because the test-seam constructor forces a choice. */
    @Autowired
    public GoogleOAuthGateway(GoogleOAuthProperties properties) {
        this(properties, defaultRestClient(), defaultVerifier(properties));
    }

    /** Test seam: injects a stubbed HTTP client and verifier. */
    GoogleOAuthGateway(
            GoogleOAuthProperties properties,
            RestClient restClient,
            GoogleIdTokenVerifier verifier) {
        this.properties = properties;
        this.restClient = restClient;
        this.verifier = verifier;
    }

    private static RestClient defaultRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory).build();
    }

    private static GoogleIdTokenVerifier defaultVerifier(GoogleOAuthProperties properties) {
        String audience = properties.clientId() == null ? "" : properties.clientId();
        return new GoogleIdTokenVerifier.Builder(
                        new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(audience))
                .build();
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        ensureConfigured();
        return UriComponentsBuilder.fromUriString(properties.authorizeUrl())
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("state", state)
                .queryParam("access_type", "online")
                .encode()
                .toUriString();
    }

    @Override
    public OAuthProfile exchangeCodeForProfile(String code) {
        ensureConfigured();
        String idToken = exchangeCode(code);
        GoogleIdToken.Payload payload = verify(idToken);
        Boolean emailVerified = payload.getEmailVerified();
        if (emailVerified == null || !emailVerified) {
            log.warn("google id_token rejected: email not verified");
            throw new BizException(IdentityErrorCode.OAUTH_TOKEN_INVALID);
        }
        return new OAuthProfile(
                OAuthProvider.GOOGLE,
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name"),
                (String) payload.get("picture"),
                payload.toString());
    }

    private String exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("grant_type", "authorization_code");
        String body;
        try {
            body =
                    restClient
                            .post()
                            .uri(properties.tokenUrl())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(String.class);
        } catch (RestClientException ex) {
            log.warn("google token exchange failed: {}", ex.getMessage());
            throw new BizException(IdentityErrorCode.OAUTH_PROVIDER_ERROR);
        }
        if (body == null || body.isBlank()) {
            log.warn("google token exchange returned an empty body");
            throw new BizException(IdentityErrorCode.OAUTH_PROVIDER_ERROR);
        }
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (!json.has("id_token") || json.get("id_token").isJsonNull()) {
            // provider error payload (e.g. invalid_grant for a reused/expired code) — never log it
            // raw
            log.warn("google token exchange returned no id_token");
            throw new BizException(IdentityErrorCode.OAUTH_TOKEN_INVALID);
        }
        return json.get("id_token").getAsString();
    }

    private GoogleIdToken.Payload verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                log.warn("google id_token verification failed (signature/iss/aud/exp)");
                throw new BizException(IdentityErrorCode.OAUTH_TOKEN_INVALID);
            }
            return token.getPayload();
        } catch (GeneralSecurityException ex) {
            log.warn("google id_token verification error: {}", ex.getMessage());
            throw new BizException(IdentityErrorCode.OAUTH_TOKEN_INVALID);
        } catch (IOException ex) {
            // cert fetch / network failure talking to Google, not a bad token
            log.warn("google cert fetch failed: {}", ex.getMessage());
            throw new BizException(IdentityErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private void ensureConfigured() {
        if (!properties.configured()) {
            log.warn("google oauth called while unconfigured");
            throw new BizException(IdentityErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }
}
