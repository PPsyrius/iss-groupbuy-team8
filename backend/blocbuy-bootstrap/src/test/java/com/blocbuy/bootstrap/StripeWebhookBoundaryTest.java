package com.blocbuy.bootstrap;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real application filters, controller, facade, Checkout service and SDK signature verification.
 */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
            "spring.datasource.username=unused",
            "spring.datasource.password=unused",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "blocbuy.payment.stripe.api-key=",
            "blocbuy.payment.stripe.webhook-secret=local-boundary-test-not-a-credential",
            "blocbuy.payment.checkout.success-url=http://localhost:5173/payment/success",
            "blocbuy.payment.checkout.cancel-url=http://localhost:5173/payment/cancel"
        })
@ActiveProfiles({"local", "stripe-sprint2"})
@AutoConfigureMockMvc
@Import(StripeWebhookBoundaryTest.LocalSessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StripeWebhookBoundaryTest {
    private static final String PATH = "/api/v1/webhooks/stripe";
    private static final String SECRET = "local-boundary-test-not-a-credential";
    private static final String UNKNOWN =
            """
            {"id":"evt_boundary","object":"event",
             "type":"customer.created","description":"Payment test","livemode":false}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private CheckoutPaymentRepository payments;
    @MockitoBean private PaymentBusinessPort business;

    @Test
    void signedUnknownNotificationIsPublicWithoutLoginOrCsrfAndDoesNotTouchBusiness()
            throws Exception {
        mvc.perform(
                        post(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "Stripe-Signature",
                                        sign(UNKNOWN, Instant.now().getEpochSecond()))
                                .content(UNKNOWN.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"));
        verifyNoInteractions(payments, business);
    }

    @Test
    void changedBodyAndExpiredSignatureAreRejectedBeforeDatabaseAccess() throws Exception {
        mvc.perform(
                        post(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "Stripe-Signature",
                                        sign(UNKNOWN, Instant.now().getEpochSecond()))
                                .content(UNKNOWN + " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_400_001"));
        mvc.perform(
                        post(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "Stripe-Signature",
                                        sign(
                                                UNKNOWN,
                                                Instant.now().minusSeconds(600).getEpochSecond()))
                                .content(UNKNOWN))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(payments, business);
    }

    @Test
    void missingHeaderReturnsSafeBadRequestInsteadOfServerError() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(UNKNOWN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
        verifyNoInteractions(payments, business);
    }

    @Test
    void oversizedPayloadIsRejectedBeforeBusinessAccess() throws Exception {
        mvc.perform(
                        post(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", "t=0,v1=invalid")
                                .content("x".repeat(262145)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(payments, business);
    }

    private static String sign(String body, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t="
                + timestamp
                + ",v1="
                + HexFormat.of()
                        .formatHex(
                                mac.doFinal(
                                        (timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalSessionStore {
        @Bean
        @Primary
        SaTokenDao localTokenDao() {
            return new SaTokenDaoDefaultImpl();
        }
    }
}
