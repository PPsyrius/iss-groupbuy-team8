package com.blocbuy.identity.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.gateway.OAuthIdentityGateway.OAuthProfile;
import com.blocbuy.identity.model.OAuthProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * Google OIDC gateway: authorize URL shape, code exchange, id_token verification outcomes. The HTTP
 * client is stubbed via a request factory and the verifier is mocked — no network, no real Google
 * certs.
 */
class GoogleOAuthGatewayTest {

    private static final GoogleOAuthProperties PROPS =
            new GoogleOAuthProperties(
                    "client-id-123",
                    "client-secret-xyz",
                    "https://api.example.com/cb",
                    null,
                    null,
                    0);

    @Test
    void buildAuthorizeUrlCarriesClientRedirectAndState() {
        GoogleOAuthGateway gateway =
                new GoogleOAuthGateway(PROPS, unusedRestClient(), mockVerifier());

        String url = gateway.buildAuthorizeUrl("state-abc");

        assertThat(url)
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
                .contains("client_id=client-id-123")
                // ':' and '/' are legal inside a query component (RFC 3986) and are left as-is
                .contains("redirect_uri=https://api.example.com/cb")
                .contains("response_type=code")
                .contains("scope=openid%20email%20profile")
                .contains("state=state-abc");
    }

    @Test
    void unconfiguredGatewayFailsFast() {
        GoogleOAuthProperties empty = new GoogleOAuthProperties(null, null, null, null, null, 0);
        GoogleOAuthGateway gateway =
                new GoogleOAuthGateway(empty, unusedRestClient(), mockVerifier());

        assertThatThrownBy(() -> gateway.buildAuthorizeUrl("s"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.OAUTH_PROVIDER_ERROR);
    }

    @Test
    void exchangeReturnsVerifiedProfile() throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-123");
        payload.setEmail("ada@example.com");
        payload.setEmailVerified(true);
        payload.put("name", "Ada");
        payload.put("picture", "https://pic");
        GoogleIdToken token = mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload);
        GoogleIdTokenVerifier verifier = mockVerifier();
        when(verifier.verify("good-token")).thenReturn(token);
        RestClient restClient = stubRestClient("{\"id_token\":\"good-token\"}", HttpStatus.OK);
        GoogleOAuthGateway gateway = new GoogleOAuthGateway(PROPS, restClient, verifier);

        OAuthProfile profile = gateway.exchangeCodeForProfile("code");

        assertThat(profile.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(profile.subject()).isEqualTo("sub-123");
        assertThat(profile.email()).isEqualTo("ada@example.com");
        assertThat(profile.displayName()).isEqualTo("Ada");
        assertThat(profile.avatarUrl()).isEqualTo("https://pic");
        assertThat(profile.rawProfileJson()).contains("sub-123");
    }

    @Test
    void exchangeMapsProviderErrorToTokenInvalid() throws Exception {
        // e.g. reused/expired authorization code: Google answers with an error payload
        RestClient restClient =
                stubRestClient("{\"error\":\"invalid_grant\"}", HttpStatus.BAD_REQUEST);
        GoogleOAuthGateway gateway = new GoogleOAuthGateway(PROPS, restClient, mockVerifier());

        assertThatThrownBy(() -> gateway.exchangeCodeForProfile("code"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isIn(
                        IdentityErrorCode.OAUTH_TOKEN_INVALID,
                        IdentityErrorCode.OAUTH_PROVIDER_ERROR);
    }

    @Test
    void failedVerificationMapsToTokenInvalid() throws Exception {
        GoogleIdTokenVerifier verifier = mockVerifier();
        when(verifier.verify("bad-token")).thenReturn(null);
        RestClient restClient = stubRestClient("{\"id_token\":\"bad-token\"}", HttpStatus.OK);
        GoogleOAuthGateway gateway = new GoogleOAuthGateway(PROPS, restClient, verifier);

        assertThatThrownBy(() -> gateway.exchangeCodeForProfile("code"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.OAUTH_TOKEN_INVALID);
    }

    @Test
    void unverifiedEmailIsRejected() throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-123");
        payload.setEmail("ada@example.com");
        payload.setEmailVerified(false);
        GoogleIdToken token = mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload);
        GoogleIdTokenVerifier verifier = mockVerifier();
        when(verifier.verify("tok")).thenReturn(token);
        RestClient restClient = stubRestClient("{\"id_token\":\"tok\"}", HttpStatus.OK);
        GoogleOAuthGateway gateway = new GoogleOAuthGateway(PROPS, restClient, verifier);

        assertThatThrownBy(() -> gateway.exchangeCodeForProfile("code"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.OAUTH_TOKEN_INVALID);
    }

    @Test
    void networkFailureMapsToProviderError() {
        RestClient restClient =
                RestClient.builder()
                        .requestFactory(
                                (uri, method) -> {
                                    throw new IOException("connection refused");
                                })
                        .build();
        GoogleOAuthGateway gateway = new GoogleOAuthGateway(PROPS, restClient, mockVerifier());

        assertThatThrownBy(() -> gateway.exchangeCodeForProfile("code"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.OAUTH_PROVIDER_ERROR);
    }

    private static RestClient stubRestClient(String json, HttpStatus status) {
        return RestClient.builder()
                .requestFactory(
                        (uri, method) -> {
                            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
                            request.setResponse(
                                    new MockClientHttpResponse(
                                            json.getBytes(StandardCharsets.UTF_8), status));
                            return request;
                        })
                .build();
    }

    private static RestClient unusedRestClient() {
        return RestClient.builder().build();
    }

    private static GoogleIdTokenVerifier mockVerifier() {
        return mock(GoogleIdTokenVerifier.class);
    }
}
