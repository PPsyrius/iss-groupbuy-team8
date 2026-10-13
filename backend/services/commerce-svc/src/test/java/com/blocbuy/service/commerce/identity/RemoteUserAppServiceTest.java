package com.blocbuy.service.commerce.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.exception.ErrorCode;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.exception.IdentityUnavailableException;
import com.blocbuy.platform.TraceIdPropagationInterceptor;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The commerce-svc identity client against a stub identity on a real socket (#468): identity's two
 * business refusals map to the local error codes, and every other answer or transport failure,
 * timeouts included, is an {@link IdentityUnavailableException}.
 */
class RemoteUserAppServiceTest {

    private static final UUID USER = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final String ACTIVE = "/internal/users/" + USER + "/active";
    private static final String NICKNAME = "/internal/users/" + USER + "/public-nickname";
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    @RegisterExtension
    static final WireMockExtension IDENTITY =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final RemoteUserAppService service = clientOf(URI.create(IDENTITY.baseUrl()));

    @AfterEach
    void clearTrace() {
        MDC.clear();
    }

    @Test
    void anActiveAccountPasses() {
        stub(ACTIVE, envelope(200, CommonErrorCode.SUCCESS, null));

        service.requireActive(USER);

        IDENTITY.verify(getRequestedFor(urlEqualTo(ACTIVE)));
    }

    @Test
    void aMissingAccountIsTheLocalNotFoundRefusal() {
        stub(ACTIVE, envelope(404, IdentityErrorCode.USER_NOT_FOUND, null));

        assertRefused(IdentityErrorCode.USER_NOT_FOUND);
    }

    @Test
    void aDisabledAccountIsTheLocalDisabledRefusal() {
        stub(ACTIVE, envelope(403, IdentityErrorCode.ACCOUNT_DISABLED, null));

        assertRefused(IdentityErrorCode.ACCOUNT_DISABLED);
    }

    /** A 404 from a missing route must never refund a paying buyer as "user not found". */
    @Test
    void aNotFoundWithoutTheIdentityCodeIsAnOutage() {
        stub(ACTIVE, envelope(404, CommonErrorCode.NOT_FOUND, null));

        assertUnavailable(() -> service.requireActive(USER));
    }

    @Test
    void aRefusalStatusWithAnotherIdentityCodeIsAnOutage() {
        stub(ACTIVE, envelope(403, IdentityErrorCode.USER_NOT_FOUND, null));

        assertUnavailable(() -> service.requireActive(USER));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 409, 500, 502, 503})
    void anyOtherStatusIsAnOutage(int status) {
        stub(ACTIVE, aResponse().withStatus(status).withBody("upstream says no"));

        assertThatThrownBy(() -> service.requireActive(USER))
                .isInstanceOf(IdentityUnavailableException.class)
                .hasMessageContaining("answered " + status);
    }

    @Test
    void anEmptyRefusalBodyIsAnOutage() {
        stub(ACTIVE, aResponse().withStatus(403).withHeader("Content-Type", "application/json"));

        assertUnavailable(() -> service.requireActive(USER));
    }

    @Test
    void anUnreadableBodyIsAnOutage() {
        stub(
                ACTIVE,
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{not json"));

        assertUnavailable(() -> service.requireActive(USER));
    }

    @Test
    void aSlowIdentityTimesOutWithinTheReadTimeout() {
        stub(ACTIVE, envelope(200, CommonErrorCode.SUCCESS, null).withFixedDelay(2_000));

        long started = System.nanoTime();
        assertThatThrownBy(() -> service.requireActive(USER))
                .isInstanceOf(IdentityUnavailableException.class)
                .hasCauseInstanceOf(ResourceAccessException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .isLessThan(READ_TIMEOUT.plusMillis(1_000));
    }

    @Test
    void aDroppedConnectionIsAnOutage() {
        stub(ACTIVE, aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

        assertUnavailable(() -> service.requireActive(USER));
    }

    @Test
    void aRefusedConnectionIsAnOutage() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        RemoteUserAppService unreachable = clientOf(URI.create("http://localhost:" + closedPort));

        assertThatThrownBy(() -> unreachable.requireActive(USER))
                .isInstanceOf(IdentityUnavailableException.class)
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    @Test
    void theCurrentTraceIdTravelsWithTheCall() {
        stub(ACTIVE, envelope(200, CommonErrorCode.SUCCESS, null));
        MDC.put(ApiConstants.TRACE_ID_MDC_KEY, "trace-468");

        service.requireActive(USER);

        IDENTITY.verify(
                getRequestedFor(urlEqualTo(ACTIVE))
                        .withHeader(ApiConstants.TRACE_ID_HEADER, equalTo("trace-468")));
    }

    @Test
    void aShowableNicknameIsReturned() {
        stub(NICKNAME, envelope(200, CommonErrorCode.SUCCESS, "{\"nickname\":\"Tester\"}"));

        assertThat(service.publicNickname(USER)).contains("Tester");
    }

    @Test
    void aNullNicknameIsEmpty() {
        stub(NICKNAME, envelope(200, CommonErrorCode.SUCCESS, "{\"nickname\":null}"));

        assertThat(service.publicNickname(USER)).isEmpty();
    }

    @Test
    void aNicknameAnswerWithoutDataIsAnOutage() {
        stub(NICKNAME, envelope(200, CommonErrorCode.SUCCESS, null));

        assertUnavailable(() -> service.publicNickname(USER));
    }

    @Test
    void aFailedNicknameLookupIsAnOutage() {
        stub(NICKNAME, aResponse().withStatus(503));

        assertUnavailable(() -> service.publicNickname(USER));
    }

    @Test
    void theSessionProfileIsNotServedRemotely() {
        assertThatThrownBy(() -> service.currentUser(USER))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void timeoutsDefaultToTheServiceConnectBudget() {
        IdentityClientProperties defaults =
                new Binder(
                                new MapConfigurationPropertySource(
                                        Map.of(
                                                "blocbuy.identity.base-url",
                                                "http://identity-svc:8589")))
                        .bind("blocbuy.identity", IdentityClientProperties.class)
                        .get();

        assertThat(defaults.baseUrl()).isEqualTo(URI.create("http://identity-svc:8589"));
        assertThat(defaults.connectTimeout()).isEqualTo(Duration.ofMillis(500));
        assertThat(defaults.readTimeout()).isEqualTo(Duration.ofMillis(1_500));
    }

    /** Built the way the configuration builds it, plus the platform's trace interceptor. */
    private static RemoteUserAppService clientOf(URI baseUrl) {
        RestClient.Builder builder =
                RestClient.builder().requestInterceptor(new TraceIdPropagationInterceptor());
        return new RemoteUserAppService(
                RemoteIdentityConfiguration.identityClient(
                        builder,
                        new IdentityClientProperties(
                                baseUrl, Duration.ofMillis(500), READ_TIMEOUT)));
    }

    private static void stub(String path, ResponseDefinitionBuilder response) {
        IDENTITY.stubFor(get(urlEqualTo(path)).willReturn(response));
    }

    private static ResponseDefinitionBuilder envelope(int status, ErrorCode code, String data) {
        String body =
                "{\"code\":\""
                        + code.code()
                        + "\",\"message\":\""
                        + code.defaultMessage()
                        + "\""
                        + (data == null ? "" : ",\"data\":" + data)
                        + "}";
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    private void assertRefused(IdentityErrorCode expected) {
        assertThatThrownBy(() -> service.requireActive(USER))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(expected);
    }

    private static void assertUnavailable(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(IdentityUnavailableException.class);
    }
}
