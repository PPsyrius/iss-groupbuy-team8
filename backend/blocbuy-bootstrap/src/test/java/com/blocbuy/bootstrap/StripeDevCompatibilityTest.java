package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.service.DisabledStripePaymentAppService;
import com.blocbuy.payment.service.PaymentAppService;
import com.blocbuy.payment.service.StripeCheckoutService;
import com.blocbuy.payment.service.StripePaymentAppService;
import com.blocbuy.payment.web.StripeWebhookController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Exactly one webhook facade is selected at the unchanged public entry in each profile. */
class StripeDevCompatibilityTest {
    @Test
    void featureOffVerifiesIrrelevantEventsWithoutCheckout() {
        CheckoutGateway gateway = mock(CheckoutGateway.class);
        when(gateway.verifyMoneyWebhook("body", "signature")).thenReturn(false);
        try (var context = context(false, gateway, null)) {
            assertThat(context.getBeansOfType(PaymentAppService.class)).hasSize(1);
            assertThat(context.getBean(PaymentAppService.class))
                    .isInstanceOf(DisabledStripePaymentAppService.class);
            assertThat(context.getBeansOfType(StripePaymentAppService.class)).isEmpty();
            context.getBean(StripeWebhookController.class).handleStripeEvent("body", "signature");
            verify(gateway).verifyMoneyWebhook("body", "signature");
        }
    }

    @Test
    void featureOffRejectsARelevantEvent() {
        CheckoutGateway gateway = mock(CheckoutGateway.class);
        when(gateway.verifyMoneyWebhook("body", "signature")).thenReturn(true);
        try (var context = context(false, gateway, null)) {
            assertThatThrownBy(
                            () ->
                                    context.getBean(StripeWebhookController.class)
                                            .handleStripeEvent("body", "signature"))
                    .isInstanceOf(BizException.class);
        }
    }

    @Test
    void featureOnUsesCheckoutThroughTheOriginalInterfaceAndController() {
        StripeCheckoutService checkout = mock(StripeCheckoutService.class);
        try (var context = context(true, mock(CheckoutGateway.class), checkout)) {
            assertThat(context.getBeansOfType(PaymentAppService.class)).hasSize(1);
            assertThat(context.getBean(PaymentAppService.class))
                    .isInstanceOf(StripePaymentAppService.class);
            assertThat(context.getBeansOfType(DisabledStripePaymentAppService.class)).isEmpty();
            context.getBean(StripeWebhookController.class).handleStripeEvent("raw body", "sig");
            verify(checkout).handleWebhook("raw body", "sig");
        }
    }

    @Test
    void enabledFacadeWithoutCheckoutNeverAcknowledgesAnUnprocessedPayment() {
        try (var context = context(true, mock(CheckoutGateway.class), null)) {
            assertThatThrownBy(
                            () ->
                                    context.getBean(StripeWebhookController.class)
                                            .handleStripeEvent("body", "sig"))
                    .isInstanceOf(BizException.class);
        }
    }

    private static AnnotationConfigApplicationContext context(
            boolean enabled, CheckoutGateway gateway, StripeCheckoutService checkout) {
        var context = new AnnotationConfigApplicationContext();
        if (enabled) {
            context.getEnvironment().setActiveProfiles("stripe-sprint2");
        }
        context.registerBean(CheckoutGateway.class, () -> gateway);
        if (checkout != null) {
            context.registerBean(StripeCheckoutService.class, () -> checkout);
        }
        context.register(
                DisabledStripePaymentAppService.class,
                StripePaymentAppService.class,
                StripeWebhookController.class);
        context.refresh();
        return context;
    }
}
