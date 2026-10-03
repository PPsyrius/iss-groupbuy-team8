package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.gateway.PaymentIntentPort;
import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.CheckoutQuote;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.repository.CheckoutPaymentRepository.ReceiptSnapshot;
import com.blocbuy.payment.repository.RefundRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Customer-facing orchestration: intents, checkout preparation and receipt mapping. */
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
        when(repository.receipt(id))
                .thenReturn(
                        Optional.of(
                                new ReceiptSnapshot(
                                        userId,
                                        payment.status(),
                                        quote.amount(),
                                        quote.currency(),
                                        null,
                                        null,
                                        null,
                                        null)));
        when(refunds.status(id)).thenReturn("processing");
        var receipt = service.receipt(userId, id);
        verify(repository).receipt(id);
        verify(refunds).status(id);
        verifyNoMoreInteractions(repository);
        assertThat(receipt.outcome()).isEqualTo("PENDING");
        assertThat(receipt.amount()).isEqualTo("10.00");
        assertThat(receipt.currency()).isEqualTo("sgd");
        assertThat(receipt.refundPending()).isTrue();
        assertThat(receipt.refundStatus()).isEqualTo("PROCESSING");
        // No PaymentIntentPort bean: the receipt is still answered, without a listing link.
        assertThat(receipt.productId()).isNull();
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
    void intentAndCheckoutDelegationSucceedsWhenPortsAreAvailable() {
        var beans = new DefaultListableBeanFactory();
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var port = mock(PaymentIntentPort.class);
        var checkout = mock(StripeCheckoutService.class);
        beans.registerSingleton("port", port);
        beans.registerSingleton("checkout", checkout);
        var service =
                new PaymentBrowserService(
                        beans.getBeanProvider(PaymentIntentPort.class),
                        beans.getBeanProvider(StripeCheckoutService.class),
                        mock(CheckoutPaymentRepository.class),
                        mock(RefundRepository.class),
                        tx);
        UUID user = UUID.randomUUID();
        UUID intent = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(port.createIntent(user, "START", intent, "ref-1", null)).thenReturn(paymentId);
        assertThat(service.createIntent(user, "START", intent, "ref-1")).isEqualTo(paymentId);
        // A shown quote travels to the port untouched; payment never interprets it.
        UUID quote = UUID.randomUUID();
        UUID quoted = UUID.randomUUID();
        when(port.createIntent(user, "START", intent, null, quote)).thenReturn(quoted);
        assertThat(service.createIntent(user, "START", intent, null, quote)).isEqualTo(quoted);
        var result =
                new StripeCheckoutService.CheckoutResult(
                        paymentId, "https://checkout.stripe.com/c/pay/test", "CREATED");
        when(checkout.prepare(user, intent, "key")).thenReturn(result);
        assertThat(service.prepare(user, intent, "key")).isSameAs(result);
    }

    @Test
    void intentPortFailuresAreMappedToBusinessErrors() {
        var beans = new DefaultListableBeanFactory();
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var port = mock(PaymentIntentPort.class);
        beans.registerSingleton("port", port);
        var service =
                new PaymentBrowserService(
                        beans.getBeanProvider(PaymentIntentPort.class),
                        beans.getBeanProvider(StripeCheckoutService.class),
                        mock(CheckoutPaymentRepository.class),
                        mock(RefundRepository.class),
                        tx);
        UUID id = UUID.randomUUID();
        when(port.createIntent(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("invalid"));
        assertThatThrownBy(() -> service.createIntent(id, "START", id, null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void receiptOfAnotherBuyersPaymentIsNotFound() {
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
                        CheckoutPayment.Status.CREATED,
                        0);
        when(repository.receipt(id))
                .thenReturn(
                        Optional.of(
                                new ReceiptSnapshot(
                                        userId,
                                        payment.status(),
                                        quote.amount(),
                                        quote.currency(),
                                        null,
                                        null,
                                        null,
                                        null)));
        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> service.receipt(stranger, id)).isInstanceOf(BizException.class);
        verifyNoMoreInteractions(refunds);
    }

    @Test
    void receiptSurfacesStoredOutcomeWhenPresent() {
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
        UUID dealId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        var intents = mock(PaymentIntentPort.class);
        beans.registerSingleton("intents", intents);
        when(intents.purchasedProduct(userId, intentId)).thenReturn(Optional.of(productId));
        when(repository.receipt(id))
                .thenReturn(
                        Optional.of(
                                new ReceiptSnapshot(
                                        userId,
                                        payment.status(),
                                        quote.amount(),
                                        quote.currency(),
                                        "FULFILLED",
                                        dealId,
                                        orderId,
                                        intentId)));
        when(refunds.status(id)).thenReturn(null);
        var receipt = service.receipt(userId, id);
        assertThat(receipt.outcome()).isEqualTo("FULFILLED");
        assertThat(receipt.dealId()).isEqualTo(dealId);
        assertThat(receipt.orderId()).isEqualTo(orderId);
        assertThat(receipt.refundPending()).isFalse();
        assertThat(receipt.productId()).isEqualTo(productId);
        verify(intents).purchasedProduct(userId, intentId);
        // An intent the order side cannot attribute to this buyer still answers the receipt.
        when(intents.purchasedProduct(userId, intentId)).thenReturn(Optional.empty());
        var unlinked = service.receipt(userId, id);
        assertThat(unlinked.productId()).isNull();
        assertThat(unlinked.orderId()).isEqualTo(orderId);
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
        assertThatThrownBy(() -> service.createIntent(id, "START", id, null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.prepare(id, id, "key")).isInstanceOf(BizException.class);
        var checkout = mock(StripeCheckoutService.class);
        beans.registerSingleton("checkout", checkout);
        when(checkout.prepare(id, id, "key")).thenThrow(new IllegalArgumentException("invalid"));
        assertThatThrownBy(() -> service.prepare(id, id, "key")).isInstanceOf(BizException.class);
    }
}
