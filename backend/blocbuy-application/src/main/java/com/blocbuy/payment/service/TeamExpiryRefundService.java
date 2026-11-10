package com.blocbuy.payment.service;

import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Records all refund obligations while the expired team remains locked. */
public final class TeamExpiryRefundService {
    private final CheckoutPaymentRepository repository;
    private final PaymentBusinessPort business;
    private final TransactionTemplate tx;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Injected repository port is intentionally shared; it is not caller-owned data to copy.")
    public TeamExpiryRefundService(
            CheckoutPaymentRepository repository,
            PaymentBusinessPort business,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.business = business;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setTimeout(5);
    }

    /** Joins the caller's team-expiry transaction when invoked by the event subscriber. */
    public void recordExpiredTeam(UUID dealId) {
        tx.executeWithoutResult(status -> {
            business.lockExpiredTeam(dealId);
            repository.requireRefundsForExpiredTeam(
                    dealId, "TEAM_EXPIRED", "team-expired-" + dealId);
        });
    }
}
