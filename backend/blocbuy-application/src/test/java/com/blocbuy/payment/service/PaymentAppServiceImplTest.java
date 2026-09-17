package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.gateway.PaymentGateway;
import com.blocbuy.payment.gateway.PaymentGateway.ProviderEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Stripe webhook orchestration: ignored, accepted, and gateway failure outcomes. */
@ExtendWith(MockitoExtension.class)
class PaymentAppServiceImplTest {

    @Mock private PaymentGateway paymentGateway;

    @InjectMocks private PaymentAppServiceImpl service;

    @Test
    void webhookEventReturnsNormallyRegardlessOfRelevance() {
        when(paymentGateway.verifyWebhook("payload", "signature")).thenReturn(Optional.empty());

        service.handleStripeWebhook("payload", "signature");

        verify(paymentGateway).verifyWebhook("payload", "signature");

        when(paymentGateway.verifyWebhook("payload", "signature"))
                .thenReturn(
                        Optional.of(
                                new ProviderEvent(
                                        "evt_123", "payment_intent.succeeded", "pi_123")));

        service.handleStripeWebhook("payload", "signature");

        verify(paymentGateway, times(2)).verifyWebhook("payload", "signature");
    }

    @Test
    void webhookVerificationExceptionPropagates() {
        BizException failure = new BizException(PaymentErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        when(paymentGateway.verifyWebhook("payload", "signature")).thenThrow(failure);

        assertThatThrownBy(() -> service.handleStripeWebhook("payload", "signature"))
                .isSameAs(failure);
    }
}
