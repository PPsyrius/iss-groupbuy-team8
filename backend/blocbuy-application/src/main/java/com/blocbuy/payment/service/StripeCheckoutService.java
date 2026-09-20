package com.blocbuy.payment.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.payment.event.PaymentSucceeded;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.PaymentReviewCase;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CheckoutPayment application slice. Explicit short transactions keep provider I/O outside DB
 * locks. Not component-scanned: enable only after a real PaymentBusinessPort is supplied.
 */
public final class StripeCheckoutService {
    private final CheckoutPaymentRepository repository;
    private final CheckoutGateway gateway;
    private final PaymentBusinessPort business;
    private final TransactionTemplate tx;
    private final String successUrl;
    private final String cancelUrl;

    /** Configure bounded local transactions and fixed server-owned return URLs. */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Injected repository port is intentionally shared; it is not caller-owned data to copy.")
    public StripeCheckoutService(
            CheckoutPaymentRepository repository,
            CheckoutGateway gateway,
            PaymentBusinessPort business,
            PlatformTransactionManager transactions,
            String successUrl,
            String cancelUrl) {
        this.repository = repository;
        this.gateway = gateway;
        this.business = business;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setTimeout(10);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.successUrl = safeReturnUrl(successUrl);
        this.cancelUrl = safeReturnUrl(cancelUrl);
    }

    /** Authenticated identity and intent only; no browser amount, user-selected URL or status. */
    public CheckoutResult prepare(UUID actor, UUID intentId, String requestKey) {
        outsideTransaction();
        Objects.requireNonNull(actor);
        Objects.requireNonNull(intentId);
        CheckoutPayment payment =
                tx.execute(
                        status -> {
                            var quote = business.lockQuote(actor, intentId);
                            if (!actor.equals(quote.userId())
                                    || !intentId.equals(quote.intentId())) {
                                throw conflict();
                            }
                            Instant now = repository.now();
                            if (quote.expiredAt(now)) {
                                throw conflict();
                            }
                            var existing = repository.findRequest(actor, requestKey);
                            if (existing.isPresent()) {
                                CheckoutPayment prior = existing.get();
                                if (!prior.quote().equals(quote)
                                        || prior.status() != CheckoutPayment.Status.CREATED) {
                                    throw conflict();
                                }
                                return prior;
                            }
                            if (repository.findBlockingAttempt(intentId).isPresent()) {
                                throw conflict();
                            }
                            // One minute of headroom for Stripe's minimum 30-minute expiry
                            // validation.
                            Instant expires = now.plusSeconds(1860).truncatedTo(ChronoUnit.SECONDS);
                            if (expires.isAfter(quote.quoteValidUntil())) {
                                throw conflict();
                            }
                            UUID paymentId = UuidV7.next();
                            CheckoutPayment created =
                                    new CheckoutPayment(
                                            paymentId,
                                            quote,
                                            requestKey,
                                            now,
                                            expires,
                                            returnUrlForPayment(successUrl, paymentId),
                                            returnUrlForPayment(cancelUrl, paymentId),
                                            null,
                                            null,
                                            null,
                                            CheckoutPayment.Status.CREATED,
                                            0);
                            repository.insert(created);
                            return created;
                        });
        Objects.requireNonNull(payment);
        if (payment.sessionId() != null) {
            var current = gateway.retrieveCheckoutSession(payment.sessionId());
            return bindAndReturn(payment.id(), current);
        }
        // Never change saved expiry or reuse an old provider key beyond the safe creation window.
        if (!repository.now().isBefore(payment.createdAt().plusSeconds(55))) {
            tx.executeWithoutResult(
                    status -> repository.review(payment.id(), "CREATE_UNKNOWN", "system"));
            throw conflict();
        }
        return bindAndReturn(payment.id(), gateway.createCheckoutSession(payment));
    }

    private CheckoutResult bindAndReturn(UUID id, CheckoutGateway.Session session) {
        return tx.execute(
                status -> {
                    CheckoutPayment initial = require(id);
                    business.lockForSettlement(initial.quote());
                    CheckoutPayment current = repository.lock(id);
                    if (!matches(current, session)) {
                        throw conflict();
                    }
                    CheckoutPayment bound =
                            current.bind(session.id(), session.url(), session.paymentIntentId());
                    repository.save(bound);
                    // Do not hand out a URL after a webhook or business expiry has won the race.
                    if (current.status() != CheckoutPayment.Status.CREATED
                            || !"open".equals(session.status())
                            || current.quote().expiredAt(repository.now())
                            || session.url() == null) {
                        return new CheckoutResult(id, null, current.status().name());
                    }
                    URI uri = URI.create(session.url());
                    if (!"https".equals(uri.getScheme())
                            || !"checkout.stripe.com".equals(uri.getHost())) {
                        throw conflict();
                    }
                    return new CheckoutResult(id, session.url(), current.status().name());
                });
    }

    /** Verify outside the transaction, then atomically apply authoritative provider facts. */
    public void handleWebhook(String rawBody, String signature) {
        outsideTransaction();
        var verified = gateway.verifyCheckoutWebhook(rawBody, signature);
        if (verified.isEmpty()) {
            return;
        }
        var event = verified.get();
        if (event.liveMode() || event.account() != null) {
            throw new BizException(PaymentErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        }
        // Fetch via this account's API key. Do not trust callback metadata as a payment lookup
        // alone.
        var session = gateway.retrieveCheckoutSession(event.sessionId());
        if (!event.sessionId().equals(session.id())) {
            throw conflict();
        }
        apply(session, event.id(), event.type(), "webhook");
    }

    /** Operator-only internal entry. No HTTP endpoint accepts manual claims of payment. */
    public CheckoutGateway.Session inspect(UUID paymentId) {
        return inspect(paymentId, null);
    }

    /**
     * A candidate ID is only a lookup hint. Provider facts and the saved binding remain
     * authoritative.
     */
    public CheckoutGateway.Session inspect(UUID paymentId, String candidateSessionId) {
        outsideTransaction();
        CheckoutPayment payment = require(paymentId);
        String sessionId = candidateSessionId == null ? payment.sessionId() : candidateSessionId;
        if (sessionId == null
                || !sessionId.matches("cs_test_[A-Za-z0-9_-]{1,240}")
                || (payment.sessionId() != null && !payment.sessionId().equals(sessionId))) {
            throw conflict();
        }
        var session = gateway.retrieveCheckoutSession(sessionId);
        if (session == null || !sessionId.equals(session.id())) {
            throw conflict();
        }
        return session;
    }

    /**
     * Read-only preview; recover fetches again and decides eligibility under the business locks.
     */
    public RecoveryPreview preview(UUID paymentId) {
        return preview(paymentId, null);
    }

    /** Preview an operator-located Session without associating it or changing payment state. */
    public RecoveryPreview preview(UUID paymentId, String candidateSessionId) {
        outsideTransaction();
        CheckoutPayment payment = require(paymentId);
        var session = inspect(paymentId, candidateSessionId);
        String action;
        if (!matches(payment, session)) {
            action = "REVIEW_PROVIDER_MISMATCH";
        } else if (session.terminalUnpaidFailure()) {
            action = "FAIL_VERIFIED_TERMINAL_UNPAID_ATTEMPT";
        } else if (session.paid()) {
            action = "VERIFY_FULFILMENT_OR_RECORD_COMPENSATION";
        } else if ("expired".equals(session.status())
                && "unpaid".equals(session.paymentStatus())
                && session.paymentIntentId() == null
                && payment.status() != CheckoutPayment.Status.SUCCEEDED) {
            action = "CANCEL_VERIFIED_UNPAID_ATTEMPT";
        } else {
            action = "OBSERVE_OR_REVIEW_CONFLICTING_FACTS";
        }
        return new RecoveryPreview(
                paymentId,
                payment.status().name(),
                session.id(),
                session.status(),
                session.paymentStatus(),
                action);
    }

    /** Retrieve provider facts again; dry-run first through inspect. Recovery is replay-safe. */
    public void recover(UUID paymentId, String operator, boolean confirm) {
        recover(paymentId, operator, confirm, null);
    }

    /**
     * Unknown creation results may supply a Session hint; all binding checks still run under locks.
     */
    public void recover(
            UUID paymentId, String operator, boolean confirm, String candidateSessionId) {
        outsideTransaction();
        if (!confirm || operator == null || !operator.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("Explicit operator confirmation required");
        }
        tx.executeWithoutResult(
                status -> {
                    repository.lock(paymentId);
                    repository.review(paymentId, "RECOVERY_REQUESTED", operator);
                    repository.assignReview(paymentId, operator, repository.now().plusSeconds(900));
                });
        try {
            var session = inspect(paymentId, candidateSessionId);
            if (!paymentId.toString().equals(session.paymentId())) {
                throw conflict();
            }
            apply(session, "recovery-" + UuidV7.next(), "manual.recovery", operator);
        } catch (RuntimeException failure) {
            tx.executeWithoutResult(
                    status -> {
                        repository.lock(paymentId);
                        repository.review(paymentId, "RECOVERY_FAILED", operator);
                        String hint =
                                candidateSessionId != null
                                                && candidateSessionId.matches(
                                                        "cs_test_[A-Za-z0-9_-]{1,240}")
                                        ? candidateSessionId
                                        : "none-or-invalid";
                        repository.recordRecovery(
                                paymentId,
                                operator,
                                "UNVERIFIED",
                                "lookupHint=" + hint + "; lookupOrApplicationFailed=true",
                                false);
                    });
            throw failure;
        }
    }

    private void apply(
            CheckoutGateway.Session session, String eventId, String type, String operator) {
        UUID id = UUID.fromString(session.paymentId());
        tx.executeWithoutResult(
                status -> {
                    if (!repository.claimEvent(eventId, type)) {
                        return;
                    }
                    CheckoutPayment initial = require(id);
                    business.lockForSettlement(initial.quote());
                    CheckoutPayment payment = repository.lock(id);
                    final CheckoutPayment.Status before = payment.status();
                    if (!matches(payment, session)) {
                        repository.review(id, "PROVIDER_MISMATCH", operator);
                        if ("manual.recovery".equals(type)) {
                            repository.recordRecovery(
                                    id,
                                    operator,
                                    "MISMATCH",
                                    "providerSession="
                                            + session.id()
                                            + "; savedSnapshotMatches=false",
                                    false);
                        }
                        repository.finishEvent(eventId, id, "REVIEW");
                        return;
                    }
                    payment = payment.bind(session.id(), null, session.paymentIntentId());
                    String result = "OBSERVE";
                    if (session.paid()) {
                        if ((payment.status() == CheckoutPayment.Status.CANCELED
                                        || payment.status() == CheckoutPayment.Status.FAILED)
                                && !"manual.recovery".equals(type)) {
                            repository.review(id, "LATE_PAID_REQUIRES_RECOVERY", operator);
                            repository.finishEvent(eventId, id, "REVIEW");
                            return;
                        }
                        payment = payment.succeed();
                        repository.save(payment);
                        String outcome = repository.outcome(id);
                        if (outcome == null) {
                            Instant now = repository.now();
                            var outcomeValue =
                                    payment.quote().expiredAt(now)
                                            ? new PaymentBusinessPort.Outcome(
                                                    null, null, "BUSINESS_EXPIRED")
                                            : business.fulfil(
                                                    new PaymentSucceeded(id, payment.quote(), now));
                            if (!outcomeValue.accepted()) {
                                repository.requireRefund(
                                        payment, outcomeValue.rejection(), eventId);
                            }
                            outcome = outcomeValue.accepted() ? "FULFILLED" : "REFUND_REQUIRED";
                            repository.recordOutcome(
                                    id, outcomeValue.dealId(), outcomeValue.orderId(), outcome);
                        }
                        result = outcome;
                    } else {
                        if (session.terminalUnpaidFailure()) {
                            payment = payment.failTerminalUnpaid();
                        } else if ("expired".equals(session.status())
                                && "unpaid".equals(session.paymentStatus())
                                && session.paymentIntentId() == null) {
                            payment = payment.cancelUnpaid();
                        }
                        repository.save(payment);
                    }
                    if ("manual.recovery".equals(type)) {
                        var evidence =
                                business.fulfilmentEvidence(
                                        new PaymentSucceeded(
                                                id, payment.quote(), repository.now()));
                        boolean paidOutcomeVerified =
                                ("FULFILLED".equals(result)
                                                && evidence
                                                        == PaymentBusinessPort.FulfilmentEvidence
                                                                .COMPLETE)
                                        || ("REFUND_REQUIRED".equals(result)
                                                && evidence
                                                        == PaymentBusinessPort.FulfilmentEvidence
                                                                .NONE
                                                && repository.refundRecorded(id));
                        boolean resolved =
                                (session.paid() && paidOutcomeVerified)
                                        || (((payment.status() == CheckoutPayment.Status.CANCELED
                                                                && "expired"
                                                                        .equals(session.status())
                                                                && "unpaid"
                                                                        .equals(
                                                                                session
                                                                                        .paymentStatus())
                                                                && session.paymentIntentId()
                                                                        == null)
                                                        || (payment.status()
                                                                        == CheckoutPayment.Status
                                                                                .FAILED
                                                                && session.terminalUnpaidFailure()))
                                                && repository.outcome(id) == null
                                                && evidence
                                                        == PaymentBusinessPort.FulfilmentEvidence
                                                                .NONE);
                        if (session.paid() && !paidOutcomeVerified) {
                            result = "DOWNSTREAM_MISSING";
                        }
                        String summary =
                                "providerSession="
                                        + session.id()
                                        + "; before="
                                        + before
                                        + "; after="
                                        + payment.status()
                                        + "; providerPaid="
                                        + session.paid()
                                        + "; providerExpired="
                                        + "expired".equals(session.status())
                                        + "; result="
                                        + result;
                        repository.recordRecovery(id, operator, result, summary, resolved);
                    }
                    repository.finishEvent(eventId, id, result);
                });
    }

    /** Run inside the caller's team-expiry transaction; never AFTER_COMMIT. */
    public void recordExpiredTeam(UUID dealId) {
        tx.executeWithoutResult(
                status -> {
                    business.lockExpiredTeam(dealId);
                    for (CheckoutPayment member : repository.paidMembers(dealId)) {
                        CheckoutPayment paid = repository.lock(member.id());
                        if (paid.status() != CheckoutPayment.Status.SUCCEEDED) {
                            throw conflict();
                        }
                        repository.requireRefund(paid, "TEAM_EXPIRED", "team-expired-" + dealId);
                    }
                });
    }

    /** Bounded local operations hook. Failures leave CREATED for the next run/manual recovery. */
    public int expireDueSessions() {
        outsideTransaction();
        int processed = 0;
        for (CheckoutPayment payment : repository.pendingBefore(repository.now(), 100)) {
            if (!payment.quote().expiredAt(repository.now()) || payment.sessionId() == null) {
                continue;
            }
            var current = gateway.retrieveCheckoutSession(payment.sessionId());
            if ("open".equals(current.status())) {
                current = gateway.expireCheckoutSession(current.id());
            }
            apply(current, "expiry-" + UuidV7.next(), "session.cleanup", "system");
            processed++;
        }
        return processed;
    }

    /** Explicit trusted cleanup request; late paid facts still use the normal settlement engine. */
    public void expireRequestedSession(UUID paymentId) {
        outsideTransaction();
        CheckoutPayment payment = require(paymentId);
        if (payment.sessionId() == null) {
            throw conflict();
        }
        var session = gateway.retrieveCheckoutSession(payment.sessionId());
        if (!matches(payment, session)) {
            throw conflict();
        }
        if ("open".equals(session.status())) {
            session = gateway.expireCheckoutSession(session.id());
        }
        apply(session, "expiry-" + UuidV7.next(), "session.cleanup", "system");
    }

    public List<CheckoutPayment> scan() {
        return repository.pendingBefore(repository.now().minusSeconds(900), 100);
    }

    /** Read-only keyset pagination includes canceled payments awaiting money reconciliation. */
    public List<PaymentReviewCase> reviewCases(UUID afterCaseId) {
        return repository.reviewCases(afterCaseId, 100);
    }

    /** Explicit local-operator action; never an unauthenticated HTTP operation. */
    public void assignReview(UUID paymentId, String operator, Instant nextCheck) {
        if (operator == null || !operator.matches("[A-Za-z0-9._-]{1,64}") || nextCheck == null) {
            throw new IllegalArgumentException("Operator and next check time required");
        }
        tx.executeWithoutResult(
                status -> {
                    repository.lock(paymentId);
                    Instant now = repository.now();
                    if (!nextCheck.isAfter(now) || nextCheck.isAfter(now.plusSeconds(86400))) {
                        throw new IllegalArgumentException(
                                "Next check must be within the next 24 hours");
                    }
                    repository.review(paymentId, "OPERATOR_REVIEW", operator);
                    repository.assignReview(paymentId, operator, nextCheck);
                });
    }

    private boolean matches(CheckoutPayment payment, CheckoutGateway.Session session) {
        return !session.liveMode()
                && payment.id().toString().equals(session.paymentId())
                && payment.quote().intentId().toString().equals(session.intentId())
                && payment.quote().amount().movePointRight(2).longValueExact()
                        == session.amountMinor()
                && payment.quote().currency().equals(session.currency())
                && payment.sessionExpiresAt().equals(session.expiresAt())
                && (payment.sessionId() == null || payment.sessionId().equals(session.id()))
                && (payment.providerPaymentIntentId() == null
                        || payment.providerPaymentIntentId().equals(session.paymentIntentId()));
    }

    private CheckoutPayment require(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new IllegalStateException("CheckoutPayment not yet available"));
    }

    private static BizException conflict() {
        return new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID);
    }

    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Do not call provider operations inside a transaction");
        }
    }

    private static String returnUrlForPayment(String base, UUID id) {
        // Bind each return to its own receipt, including when several tabs are open.
        int fragment = base.indexOf('#');
        String head = fragment < 0 ? base : base.substring(0, fragment);
        String tail = fragment < 0 ? "" : base.substring(fragment);
        return head + (head.contains("?") ? "&" : "?") + "paymentId=" + id + tail;
    }

    private static String safeReturnUrl(String value) {
        URI uri = URI.create(value);
        boolean loopback = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (uri.getHost() == null
                || uri.getUserInfo() != null
                || !("https".equals(uri.getScheme())
                        || (loopback && "http".equals(uri.getScheme())))) {
            throw new IllegalArgumentException("Return URL must use HTTPS or local loopback HTTP");
        }
        return value;
    }

    /** Hosted destination returned to the authenticated purchasing application. */
    public record CheckoutResult(UUID paymentId, String checkoutUrl, String status) {}

    /** Read-only observations and proposed action; no payment link or secret is included. */
    public record RecoveryPreview(
            UUID paymentId,
            String localStatus,
            String sessionId,
            String sessionStatus,
            String paymentStatus,
            String proposedAction) {}
}
