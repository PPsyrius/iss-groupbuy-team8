package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.gateway.PaymentIntentPort;
import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.CheckoutQuote;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.repository.RefundRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PaymentBrowserServiceTest {
    @Test
    void receiptMapsLowercaseDatabaseRefundStateToApiVocabulary() {
        var repository = mock(CheckoutPaymentRepository.class);
        var refunds = mock(RefundRepository.class);
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var beans = new DefaultListableBeanFactory();
        var service =
                new PaymentBrowserService(
                        beans.getBeanProvider(PaymentIntentPort.class),
                        beans.getBeanProvider(StripeCheckoutService.class),
                        repository,
                        refunds,
                        tx);
        UUID userId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        var quote =
                new CheckoutQuote(
                        id,
                        userId,
                        "START",
                        UUID.randomUUID(),
                        new BigDecimal("10.00"),
                        "sgd",
                        Instant.now().plusSeconds(600),
                        Instant.now().plusSeconds(3600));
        var payment =
                new CheckoutPayment(
                        id,
                        quote,
                        "key",
                        Instant.now(),
                        Instant.now().plusSeconds(300),
                        null,
                        null,
                        null,
                        null,
                        null,
                        CheckoutPayment.Status.SUCCEEDED,
                        0);
        when(repository.findById(id)).thenReturn(Optional.of(payment));
        when(repository.outcome(id)).thenReturn(null);
        when(repository.fulfilledDealId(id)).thenReturn(null);
        when(repository.orderId(id)).thenReturn(null);
        when(refunds.status(id)).thenReturn("processing");
        var receipt = service.receipt(userId, id);
        assertThat(receipt.refundPending()).isTrue();
        assertThat(receipt.refundStatus()).isEqualTo("PROCESSING");
        when(refunds.status(id)).thenReturn("pending");
        assertThat(service.receipt(userId, id).refundPending()).isTrue();
        when(refunds.status(id)).thenReturn("succeeded");
        var settled = service.receipt(userId, id);
        assertThat(settled.refundPending()).isFalse();
        assertThat(settled.refundStatus()).isEqualTo("SUCCEEDED");
        when(refunds.status(id)).thenReturn(null);
        assertThat(service.receipt(userId, id).refundStatus()).isEqualTo("NONE");
    }

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
