package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.gateway.CheckoutGateway;
import org.junit.jupiter.api.Test;

/** The feature-off webhook verifies every event but cannot settle money notifications. */
class DisabledStripePaymentAppServiceTest {
    private final CheckoutGateway gateway = mock(CheckoutGateway.class);
    private final DisabledStripePaymentAppService service =
            new DisabledStripePaymentAppService(gateway);

    @Test
    void signedIrrelevantEventIsAcknowledged() {
        when(gateway.verifyMoneyWebhook("body", "signature")).thenReturn(false);

        service.handleStripeWebhook("body", "signature");

        verify(gateway).verifyMoneyWebhook("body", "signature");
    }

    @Test
    void relevantEventIsNotAcknowledgedWithoutSettlement() {
        when(gateway.verifyMoneyWebhook("body", "signature")).thenReturn(true);

        assertThatThrownBy(() -> service.handleStripeWebhook("body", "signature"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
    }

    @Test
    void failedSignatureVerificationIsNotAcknowledged() {
        BizException failure = new BizException(PaymentErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        when(gateway.verifyMoneyWebhook("body", "signature")).thenThrow(failure);

        assertThatThrownBy(() -> service.handleStripeWebhook("body", "signature"))
                .isSameAs(failure);
    }
}
