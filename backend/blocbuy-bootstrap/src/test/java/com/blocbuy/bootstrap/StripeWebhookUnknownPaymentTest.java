package com.blocbuy.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * A verified event naming a payment this database does not hold must be answered 2xx over real
 * HTTP, not 500. Stripe retries a non-2xx event for up to three days, and a sandbox endpoint shared
 * with another developer's machine produces such events continuously (#139).
 *
 * <p>The provider gateway is mocked here on purpose: the point is what the application does with a
 * valid event whose payment is foreign, not whether the SDK verifies a signature — that is {@link
 * StripeWebhookBoundaryTest}'s job.
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
@Import(StripeWebhookUnknownPaymentTest.LocalSessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StripeWebhookUnknownPaymentTest {

    private static final String PATH = "/api/v1/webhooks/stripe";
    private static final String SESSION = "cs_test_foreign";

    @Autowired private MockMvc mvc;
    @MockitoBean private CheckoutGateway gateway;
    @MockitoBean private CheckoutPaymentRepository payments;
    @MockitoBean private PaymentBusinessPort business;

    /** No database in this test: the point is the HTTP answer, not the rows it would write. */
    @MockitoBean private PlatformTransactionManager transactions;

    @BeforeEach
    void transactionsCommitWithoutADatabase() {
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "01990000-0000-7000-8000-00000000dead"})
    void aForeignPaymentIsAcknowledgedAndRecordedInsteadOfFailing(String paymentReference)
            throws Exception {
        when(gateway.verifyCheckoutWebhook(any(), any()))
                .thenReturn(
                        Optional.of(
                                new CheckoutGateway.Event(
                                        "evt_foreign",
                                        "checkout.session.completed",
                                        SESSION,
                                        false,
                                        null)));
        when(gateway.retrieveCheckoutSession(SESSION)).thenReturn(session(paymentReference));
        when(payments.claimEvent(eq("evt_foreign"), any())).thenReturn(true);
        when(payments.findById(any())).thenReturn(Optional.empty());

        mvc.perform(
                        post(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", "t=0,v1=verified-by-the-mock")
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"));

        verify(payments).finishEvent(eq("evt_foreign"), isNull(), eq("UNKNOWN_PAYMENT"));
        verifyNoInteractions(business);
    }

    private static CheckoutGateway.Session session(String paymentReference) {
        return new CheckoutGateway.Session(
                SESSION,
                paymentReference,
                UUID.randomUUID().toString(),
                null,
                1000,
                "sgd",
                "complete",
                "paid",
                false,
                null,
                Instant.now().plusSeconds(1800),
                "succeeded");
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
