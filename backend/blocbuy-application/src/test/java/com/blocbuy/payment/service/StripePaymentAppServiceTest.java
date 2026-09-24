package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** Delegate to transactional processing or fail closed, never acknowledge a stub. */
class StripePaymentAppServiceTest {
    @Test
    void forwardsUntouchedPayloadAndSignature() {
        ObjectProvider<StripeCheckoutService> provider = provider();
        var checkout = mock(StripeCheckoutService.class);
        when(provider.getIfAvailable()).thenReturn(checkout);
        new StripePaymentAppService(provider).handleStripeWebhook("raw payload", "signature");
        verify(checkout).handleWebhook("raw payload", "signature");
    }

    @Test
    void missingIntegrationFailsClosed() {
        var service = new StripePaymentAppService(provider());
        assertThatThrownBy(() -> service.handleStripeWebhook("raw", "signature"))
                .isInstanceOf(BizException.class);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StripeCheckoutService> provider() {
        return mock(ObjectProvider.class);
    }
}
