package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.repository.RoleRepository;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.service.PaymentBrowserService;
import com.blocbuy.payment.service.StripeCheckoutService.CheckoutResult;
import com.blocbuy.payment.service.StripeFeatureSwitch;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Real HTTP/session boundary; application and OAuth responses mocked, no remote services. */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/placeholder",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1"
        })
@AutoConfigureMockMvc
@Import(AuthControllerTest.SessionStore.class)
@DirtiesContext
class PaymentBrowserControllerTest {
    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID BUSINESS = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID INTENT = UUID.fromString("00000000-0000-4000-8000-000000000020");
    private static final UUID PAYMENT = UUID.fromString("00000000-0000-4000-8000-000000000030");
    private static final String INTENTS = "/api/v1/payments/intents";
    private static final String CHECKOUT = "/api/v1/payments/checkout-sessions";
    @Autowired private MockMvc mvc;
    @MockitoBean private PaymentBrowserService payments;
    @MockitoBean private StripeFeatureSwitch feature;
    @MockitoBean private OAuthLoginAppService oauth;
    @MockitoBean private RoleRepository roles;

    @Test
    void enabledCapabilitiesUseTheSameSwitchAsTheBrowserEndpoints() throws Exception {
        when(feature.enabled()).thenReturn(true);
        mvc.perform(get("/api/v1/payments/capabilities").cookie(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
        verifyNoInteractions(payments);
    }

    private Cookie login() throws Exception {
        when(oauth.loginWithGoogle(any(), any(), any()))
                .thenReturn(new OAuthLoginResult(USER, "Local buyer", null));
        var result =
                mvc.perform(
                                get("/api/v1/auth/oauth2/google/callback")
                                        .param("code", "test")
                                        .param("state", "test")
                                        .cookie(new Cookie("blocbuy-oauth-state", "test")))
                        .andExpect(status().isFound())
                        .andReturn();
        Cookie cookie = result.getResponse().getCookie("blocbuy-token");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return cookie;
    }

    @Test
    void anonymousCannotCreateOrReadPayments() throws Exception {
        mvc.perform(
                        post(INTENTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-BlocBuy-Request", "checkout")
                                .content(
                                        "{\"operation\":\"START\",\"businessId\":\""
                                                + BUSINESS
                                                + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/payments/" + PAYMENT)).andExpect(status().isUnauthorized());
        verifyNoInteractions(payments);
    }

    @Test
    void requiresCsrfHeaderBeforeCreatingIntent() throws Exception {
        Cookie cookie = login();
        String body = "{\"operation\":\"START\",\"businessId\":\"" + BUSINESS + "\"}";
        mvc.perform(
                        post(INTENTS)
                                .cookie(cookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(INTENTS)
                                .cookie(cookie)
                                .header("X-BlocBuy-Request", "wrong")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }

    @Test
    void rejectsClientAmountAndBuyerOverride() throws Exception {
        Cookie cookie = login();
        for (String extra : new String[] {"\"amount\":0.01", "\"buyerId\":\"" + BUSINESS + "\""}) {
            mvc.perform(
                            post(INTENTS)
                                    .cookie(cookie)
                                    .header("X-BlocBuy-Request", "checkout")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"operation\":\"START\",\"businessId\":\""
                                                    + BUSINESS
                                                    + "\","
                                                    + extra
                                                    + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(payments);
    }

    @Test
    void createsServerOwnedIntentInResultEnvelope() throws Exception {
        when(payments.createIntent(USER, "START", BUSINESS, null, null)).thenReturn(INTENT);
        mvc.perform(
                        post(INTENTS)
                                .cookie(login())
                                .header("X-BlocBuy-Request", "checkout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"operation\":\"START\",\"businessId\":\""
                                                + BUSINESS
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.intentId").value(INTENT.toString()));
        verify(payments).createIntent(USER, "START", BUSINESS, null, null);
    }

    /** UC7: the invite reference is passed through to the intent service untouched. */
    @Test
    void aJoinCarriesItsInviteReferenceToTheIntentService() throws Exception {
        when(payments.createIntent(USER, "JOIN", BUSINESS, "Ab-_09", null)).thenReturn(INTENT);
        mvc.perform(
                        post(INTENTS)
                                .cookie(login())
                                .header("X-BlocBuy-Request", "checkout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"operation\":\"JOIN\",\"businessId\":\""
                                                + BUSINESS
                                                + "\",\"ref\":\"Ab-_09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intentId").value(INTENT.toString()));
        verify(payments).createIntent(USER, "JOIN", BUSINESS, "Ab-_09", null);
    }

    /** #183: a START names the quote the buyer was shown, and it reaches the service untouched. */
    @Test
    void aStartCarriesItsQuoteIdToTheIntentService() throws Exception {
        UUID quote = UUID.fromString("00000000-0000-4000-8000-000000000040");
        when(payments.createIntent(USER, "START", BUSINESS, null, quote)).thenReturn(INTENT);
        mvc.perform(
                        post(INTENTS)
                                .cookie(login())
                                .header("X-BlocBuy-Request", "checkout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"operation\":\"START\",\"businessId\":\""
                                                + BUSINESS
                                                + "\",\"quoteId\":\""
                                                + quote
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intentId").value(INTENT.toString()));
        verify(payments).createIntent(USER, "START", BUSINESS, null, quote);
    }

    @Test
    void aMalformedQuoteIdIsRejectedBeforeAnyWork() throws Exception {
        mvc.perform(
                        post(INTENTS)
                                .cookie(login())
                                .header("X-BlocBuy-Request", "checkout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"operation\":\"START\",\"businessId\":\""
                                                + BUSINESS
                                                + "\",\"quoteId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
        verifyNoInteractions(payments);
    }

    @Test
    void aMalformedInviteReferenceIsRejectedBeforeAnyWork() throws Exception {
        Cookie cookie = login();
        for (String ref : new String[] {"has space", "a".repeat(65), ""}) {
            mvc.perform(
                            post(INTENTS)
                                    .cookie(cookie)
                                    .header("X-BlocBuy-Request", "checkout")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"operation\":\"JOIN\",\"businessId\":\""
                                                    + BUSINESS
                                                    + "\",\"ref\":\""
                                                    + ref
                                                    + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(payments);
    }

    @Test
    void checkoutUsesCookieIdentityAndRequiredIdempotencyKey() throws Exception {
        Cookie cookie = login();
        String body = "{\"intentId\":\"" + INTENT + "\"}";
        when(payments.prepare(USER, INTENT, "retry-key"))
                .thenReturn(
                        new CheckoutResult(
                                PAYMENT, "https://checkout.stripe.com/c/pay/test", "CREATED"));
        mvc.perform(
                        post(CHECKOUT)
                                .cookie(cookie)
                                .header("X-BlocBuy-Request", "checkout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(CHECKOUT)
                                .cookie(cookie)
                                .header("X-BlocBuy-Request", "checkout")
                                .header("Idempotency-Key", "retry-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT.toString()));
        verify(payments).prepare(USER, INTENT, "retry-key");
    }

    @Test
    void receiptDoesNotExposeProviderSecretsAndDeniesWrongOwner() throws Exception {
        Cookie cookie = login();
        when(payments.receipt(USER, PAYMENT))
                .thenReturn(
                        new PaymentBrowserService.Receipt(
                                PAYMENT,
                                "SUCCEEDED",
                                "FULFILLED",
                                "10.00",
                                "sgd",
                                BUSINESS,
                                INTENT,
                                false,
                                "NONE"));
        mvc.perform(get("/api/v1/payments/" + PAYMENT).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.orderId").value(INTENT.toString()))
                .andExpect(jsonPath("$.data.checkoutUrl").doesNotExist())
                .andExpect(jsonPath("$.data.providerPaymentIntentId").doesNotExist());
        when(payments.receipt(USER, PAYMENT))
                .thenThrow(new BizException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        mvc.perform(get("/api/v1/payments/" + PAYMENT).cookie(cookie))
                .andExpect(status().isNotFound());
    }
}
