package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocbuy.identity.repository.RoleRepository;
import com.blocbuy.identity.service.LoginAuthorizationService;
import com.blocbuy.identity.service.LoginAuthorizationService.Snapshot;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.service.PaymentBrowserService;
import com.stripe.Stripe;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** No database, no Stripe profile: all dependent requests must stop at the web boundary. */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
            "spring.datasource.username=unused",
            "spring.datasource.password=unused",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "blocbuy.payment.stripe.api-key=",
            "blocbuy.payment.stripe.webhook-secret=disabled-webhook-signing-fixture"
        })
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(AuthControllerTest.SessionStore.class)
@DirtiesContext
class StripeDisabledHttpTest {
    private static final String ID = "00000000-0000-4000-8000-000000000001";
    private static final String WEBHOOK = "/api/v1/webhooks/stripe";
    private static final String SECRET = "disabled-webhook-signing-fixture";
    @Autowired private MockMvc mvc;
    @MockitoBean private PaymentBrowserService payments;
    @MockitoBean private CheckoutPaymentRepository checkoutPayments;
    @MockitoBean private OAuthLoginAppService oauth;
    @MockitoBean private RoleRepository roles;
    @MockitoBean private LoginAuthorizationService authorization;

    @Autowired
    private java.util.List<
                    org.springframework.web.servlet.mvc.method.annotation
                            .RequestMappingHandlerMapping>
            mappings;

    @Test
    void localLoginAndOperatorEndpointsAreNotRegistered() {
        assertThat(
                        mappings.stream()
                                .flatMap(handler -> handler.getHandlerMethods().keySet().stream()))
                .isNotEmpty()
                .allSatisfy(mapping ->
                        assertThat(mapping.getPatternValues())
                                .noneMatch(path -> path.startsWith("/local/stripe")));
    }

    @Test
    void capabilitiesAreAuthenticatedAndDefaultOffWithoutBusinessAccess() throws Exception {
        mvc.perform(get("/api/v1/payments/capabilities")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/payments/capabilities").cookie(login()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.enabled").value(false));
        verifyNoInteractions(payments);
    }

    @Test
    void allStripeBrowserRoutesRejectBeforeTouchingServices() throws Exception {
        Cookie cookie = login();
        mvc.perform(
                        post("/api/v1/payments/intents")
                                .cookie(cookie)
                                .header("X-BlocBuy-Request", "checkout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"operation\":\"START\",\"businessId\":\"" + ID + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/payments/checkout-sessions")
                                .cookie(cookie)
                                .header("X-BlocBuy-Request", "checkout")
                                .header("Idempotency-Key", "disabled-test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"intentId\":\"" + ID + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/payments/" + ID).cookie(cookie)).andExpect(status().isNotFound());
        verifyNoInteractions(payments);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "checkout.session.completed",
                "checkout.session.async_payment_succeeded",
                "checkout.session.async_payment_failed",
                "checkout.session.expired"
            })
    void signedCheckoutEventsCannotBeAcknowledgedWhileCheckoutIsOff(String type) throws Exception {
        String body = event(type, "checkout.session");

        mvc.perform(
                        post(WEBHOOK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", sign(body))
                                .content(body))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_502_001"));
        verifyNoInteractions(checkoutPayments, payments);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "payment_intent.succeeded",
                "payment_intent.payment_failed",
                "charge.refunded",
                "refund.updated",
                "refund.failed"
            })
    void signedProviderMoneyEventsCannotBeAcknowledgedWhileCheckoutIsOff(String type)
            throws Exception {
        String body = event(type, type.substring(0, type.indexOf('.')));

        mvc.perform(
                        post(WEBHOOK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", sign(body))
                                .content(body))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_502_001"));
        verifyNoInteractions(checkoutPayments, payments);
    }

    @Test
    void signedIrrelevantEventIsAcknowledgedWithoutDatabaseAccess() throws Exception {
        String body = event("customer.created", "customer");

        mvc.perform(
                        post(WEBHOOK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", sign(body))
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"));
        verifyNoInteractions(checkoutPayments, payments);
    }

    @Test
    void signedCheckoutSessionCreationIsIrrelevantToSettlement() throws Exception {
        String body = event("checkout.session.created", "checkout.session");

        mvc.perform(
                        post(WEBHOOK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", sign(body))
                                .content(body))
                .andExpect(status().isOk());
        verifyNoInteractions(checkoutPayments, payments);
    }

    @Test
    void signedCheckoutEventWithUndecodableObjectIsNotAcknowledged() throws Exception {
        String body = event("checkout.session.completed", "customer");

        mvc.perform(
                        post(WEBHOOK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", sign(body))
                                .content(body))
                .andExpect(status().isBadGateway());
        verifyNoInteractions(checkoutPayments, payments);
    }

    @Test
    void invalidSignatureIsRejectedEvenForAnIrrelevantEvent() throws Exception {
        String body = event("customer.created", "customer");

        mvc.perform(
                        post(WEBHOOK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", sign(body))
                                .content(body + " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_400_001"));
        verifyNoInteractions(checkoutPayments, payments);
    }

    @SuppressWarnings("deprecation")
    private static String event(String type, String objectType) {
        return """
        {"id":"evt_disabled","object":"event","api_version":"%s",\
        "livemode":false,"type":"%s","data":{"object":{"id":"cs_test_disabled",\
        "object":"%s"}}}
        """
                .formatted(Stripe.API_VERSION, type, objectType);
    }

    private static String sign(String body) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }

    private Cookie login() throws Exception {
        when(authorization.snapshot(UUID.fromString(ID)))
                .thenReturn(new Snapshot(java.util.List.of(), java.util.List.of()));
        when(oauth.loginWithGoogle(any(), any(), any()))
                .thenReturn(new OAuthLoginResult(UUID.fromString(ID), "Test buyer", null));
        return mvc.perform(
                        get("/api/v1/auth/oauth2/google/callback")
                                .param("code", "test")
                                .param("state", "test")
                                .cookie(new Cookie("blocbuy-oauth-state", "test")))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getCookie("blocbuy-token");
    }
}
