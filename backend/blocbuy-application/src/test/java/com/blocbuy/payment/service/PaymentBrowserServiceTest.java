package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.gateway.PaymentIntentPort;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.repository.RefundRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PaymentBrowserServiceTest {
    @Test
    void unavailablePortsAndInvalidCheckoutAreMappedToBusinessErrors() {
        var beans = new DefaultListableBeanFactory();
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var service =
                new PaymentBrowserService(
                        beans.getBeanProvider(PaymentIntentPort.class),
                        beans.getBeanProvider(StripeCheckoutService.class),
                        mock(CheckoutPaymentRepository.class),
                        mock(RefundRepository.class),
                        tx);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.createIntent(id, "START", id))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.prepare(id, id, "key")).isInstanceOf(BizException.class);
        var checkout = mock(StripeCheckoutService.class);
        beans.registerSingleton("checkout", checkout);
        when(checkout.prepare(id, id, "key")).thenThrow(new IllegalArgumentException("invalid"));
        assertThatThrownBy(() -> service.prepare(id, id, "key")).isInstanceOf(BizException.class);
    }
}
