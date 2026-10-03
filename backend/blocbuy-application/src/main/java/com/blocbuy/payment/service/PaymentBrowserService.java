package com.blocbuy.payment.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.gateway.RateLimitGateway;
import com.blocbuy.common.gateway.RateLimitGateway.RateLimit;
import com.blocbuy.order.model.PurchaseOperation;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.gateway.PaymentIntentPort;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Customer-facing orchestration. Identity comes from Sa-Token, never a request buyerId. */
@Service
@RequiredArgsConstructor
public class PaymentBrowserService {
    private final ObjectProvider<PaymentIntentPort> intents;
    private final ObjectProvider<StripeCheckoutService> checkout;
    private final CheckoutPaymentRepository repository;
    private final com.blocbuy.payment.repository.RefundRepository refunds;
    private final PlatformTransactionManager transactions;
    private final RateLimitGateway rateLimits;

    static final RateLimit JOIN_LIMIT = new RateLimit("join-intent", 60, Duration.ofMinutes(1));
    static final RateLimit CHECKOUT_LIMIT =
            new RateLimit("payment-initiation", 60, Duration.ofMinutes(1));

    public UUID createIntent(UUID user, String operation, UUID businessId, String inviteRef) {
        return createIntent(user, operation, businessId, inviteRef, null);
    }

    /** The quote id is passed through untouched; the port prices the intent at it or refuses. */
    public UUID createIntent(
            UUID user, String operation, UUID businessId, String inviteRef, UUID quoteId) {
        PaymentIntentPort port = intents.getIfAvailable();
        if (port == null) throw new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID);
        if (PurchaseOperation.JOIN.code().equals(operation)
                && !rateLimits.tryAcquire(JOIN_LIMIT, user + ":" + businessId)) {
            throw new BizException(CommonErrorCode.RATE_LIMITED);
        }
        TransactionTemplate tx = new TransactionTemplate(transactions);
        tx.setTimeout(10);
        try {
            return tx.execute(
                    status -> port.createIntent(user, operation, businessId, inviteRef, quoteId));
        } catch (IllegalArgumentException failure) {
            throw new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID);
        }
    }

    public StripeCheckoutService.CheckoutResult prepare(UUID user, UUID intent, String requestKey) {
        StripeCheckoutService service = checkout.getIfAvailable();
        if (service == null) throw new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID);
        if (!rateLimits.tryAcquire(CHECKOUT_LIMIT, user.toString())) {
            throw new BizException(CommonErrorCode.RATE_LIMITED);
        }
        try {
            return service.prepare(user, intent, requestKey);
        } catch (IllegalArgumentException failure) {
            throw new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID);
        }
    }

    public record Receipt(
            UUID paymentId,
            String status,
            String outcome,
            String amount,
            String currency,
            UUID dealId,
            UUID orderId,
            boolean refundPending,
            String refundStatus) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public Receipt receipt(UUID user, UUID id) {
        var snapshot =
                repository
                        .receipt(id)
                        .filter(view -> view.userId().equals(user))
                        .orElseThrow(() -> new BizException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        // Database lifecycle codes are lowercase (V2); the API vocabulary stays uppercase.
        String refundStatus = refunds.status(id);
        return new Receipt(
                id,
                snapshot.status().name(),
                snapshot.outcome() == null ? "PENDING" : snapshot.outcome(),
                snapshot.amount().toPlainString(),
                snapshot.currency(),
                snapshot.dealId(),
                snapshot.orderId(),
                "pending".equals(refundStatus) || "processing".equals(refundStatus),
                refundStatus == null ? "NONE" : refundStatus.toUpperCase(java.util.Locale.ROOT));
    }
}
