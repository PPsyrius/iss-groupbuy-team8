package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.PaymentGateway;
import com.blocbuy.payment.model.Payment;
import com.blocbuy.payment.persistence.DevPaymentRepositoryAdapter;
import com.blocbuy.payment.persistence.PaymentRepositoryImpl;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.repository.PaymentRepository;
import com.blocbuy.payment.service.PaymentAppService;
import com.blocbuy.payment.service.PaymentAppServiceImpl;
import com.blocbuy.payment.service.StripeCheckoutService;
import com.blocbuy.payment.service.StripePaymentAppService;
import com.blocbuy.payment.web.StripeWebhookController;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Compiles against the dev contracts and exercises profile selection at the unchanged entry. */
class StripeDevCompatibilityTest {
    @Test
    void existingStringIdsAndRepositoryCallersNeedNoChanges() {
        PaymentRepository repository = id -> Optional.of(Payment.of(id));
        assertThat(repository.findById("existing-non-uuid-id").orElseThrow().id())
                .isEqualTo("existing-non-uuid-id");
        assertThat(CheckoutGateway.class.isAssignableFrom(PaymentGateway.class)).isFalse();
    }

    @Test
    void featureOffKeepsOriginalWebhookImplementation() {
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.verifyWebhook("body", "signature")).thenReturn(Optional.empty());
        try (var context = context(false, gateway, null)) {
            assertThat(context.getBean(PaymentAppService.class))
                    .isInstanceOf(PaymentAppServiceImpl.class);
            assertThat(context.getBeansOfType(StripePaymentAppService.class)).isEmpty();
            assertThat(context.getBean(PaymentRepository.class))
                    .isInstanceOf(PaymentRepositoryImpl.class);
            context.getBean(StripeWebhookController.class).handleStripeEvent("body", "signature");
            verify(gateway).verifyWebhook("body", "signature");
        }
    }

    @Test
    void featureOnUsesCheckoutThroughTheOriginalInterfaceAndController() {
        StripeCheckoutService checkout = mock(StripeCheckoutService.class);
        try (var context = context(true, mock(PaymentGateway.class), checkout)) {
            assertThat(context.getBeansOfType(PaymentAppService.class)).hasSize(2);
            assertThat(context.getBean(PaymentAppService.class))
                    .isInstanceOf(StripePaymentAppService.class);
            assertThat(context.getBean(PaymentRepository.class))
                    .isInstanceOf(DevPaymentRepositoryAdapter.class);
            context.getBean(StripeWebhookController.class).handleStripeEvent("raw body", "sig");
            verify(checkout).handleWebhook("raw body", "sig");
        }
    }

    @Test
    void enabledFacadeWithoutCheckoutNeverAcknowledgesAnUnprocessedPayment() {
        try (var context = context(true, mock(PaymentGateway.class), null)) {
            assertThatThrownBy(
                            () ->
                                    context.getBean(StripeWebhookController.class)
                                            .handleStripeEvent("body", "sig"))
                    .isInstanceOf(BizException.class);
        }
    }

    private static AnnotationConfigApplicationContext context(
            boolean enabled, PaymentGateway gateway, StripeCheckoutService checkout) {
        var context = new AnnotationConfigApplicationContext();
        if (enabled) {
            context.getEnvironment().setActiveProfiles("stripe-sprint2");
        }
        context.registerBean(PaymentGateway.class, () -> gateway);
        context.registerBean(
                CheckoutPaymentRepository.class, () -> mock(CheckoutPaymentRepository.class));
        if (checkout != null) {
            context.registerBean(StripeCheckoutService.class, () -> checkout);
        }
        context.register(
                PaymentAppServiceImpl.class,
                PaymentRepositoryImpl.class,
                DevPaymentRepositoryAdapter.class,
                StripePaymentAppService.class,
                StripeWebhookController.class);
        context.refresh();
        return context;
    }
}
