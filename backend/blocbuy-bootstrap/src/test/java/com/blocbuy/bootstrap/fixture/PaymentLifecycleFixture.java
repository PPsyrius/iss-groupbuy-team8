package com.blocbuy.bootstrap.fixture;

import com.blocbuy.groupbuy.service.GroupDealSettlementSupport;
import com.blocbuy.payment.service.PaymentBrowserService;
import com.blocbuy.payment.service.StripeCheckoutService;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test-only expiry driver; obligations commit with team expiry, never AFTER_COMMIT.
 *
 * <p>What used to be here as well — a whole second implementation of the business ports — is gone
 * since 2026-09-22. Intents now come from the production {@code PaymentIntentPort} through {@link
 * PaymentBrowserService}, and the team lock comes from groupbuy's own facade. This class only does
 * what no production code does: force a team past its deadline and drive the local sweeper.
 */
public final class PaymentLifecycleFixture {
    private final JdbcTemplate jdbc;
    private final PaymentBrowserService browser;
    private final GroupDealSettlementSupport teams;
    private final StripeCheckoutService payments;
    private final TransactionTemplate tx;

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Constructor-injected shared Spring JDBC collaborator; intentionally not copied")
    public PaymentLifecycleFixture(
            JdbcTemplate jdbc,
            PaymentBrowserService browser,
            GroupDealSettlementSupport teams,
            StripeCheckoutService payments,
            PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.browser = browser;
        this.teams = teams;
        this.payments = payments;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setTimeout(10);
    }

    /** The production intent path; kept here only so tests read the same way they used to. */
    public UUID intent(UUID user, String operation, UUID businessId) {
        return browser.createIntent(user, operation, businessId);
    }

    public void expireTeam(UUID deal) {
        tx.executeWithoutResult(
                status -> {
                    teams.lockTeam(deal);
                    jdbc.update(
                            "UPDATE biz_group_deal SET status='expired',version=version+1,update_time=clock_timestamp() WHERE id=? AND status='open' AND expire_time<=clock_timestamp() AND current_size<target_size",
                            deal);
                    if ("expired"
                            .equals(
                                    jdbc.queryForObject(
                                            "SELECT status FROM biz_group_deal WHERE id=?",
                                            String.class,
                                            deal))) {
                        payments.recordExpiredTeam(deal);
                    }
                });
    }

    /** Durable pending requests survive process restarts; failures retry individually after 60s. */
    public void sweep() {
        for (UUID deal :
                jdbc.queryForList(
                        "SELECT id FROM biz_group_deal WHERE status='open' AND expire_time<=clock_timestamp() AND NOT deleted ORDER BY id LIMIT 100",
                        UUID.class)) {
            expireTeam(deal);
        }
        jdbc.update(
                "INSERT INTO stripe_local_expiry_request(payment_id) SELECT p.id FROM biz_payment p WHERE p.status='created' AND p.intent_id IS NOT NULL AND (p.business_deadline<=clock_timestamp() OR p.quote_valid_until<=clock_timestamp() OR (p.operation='JOIN' AND EXISTS(SELECT 1 FROM biz_group_deal g WHERE g.id=p.business_id AND (g.status<>'open' OR g.deleted)))) ON CONFLICT DO NOTHING");
        for (UUID payment :
                jdbc.queryForList(
                        "SELECT payment_id FROM stripe_local_expiry_request WHERE completed_at IS NULL AND next_attempt_at<=clock_timestamp() ORDER BY requested_at,payment_id LIMIT 100",
                        UUID.class)) {
            try {
                payments.expireRequestedSession(payment);
                jdbc.update(
                        "UPDATE stripe_local_expiry_request SET completed_at=CASE WHEN (SELECT status FROM biz_payment WHERE id=?)<>'created' THEN clock_timestamp() ELSE NULL END,attempts=attempts+1,next_attempt_at=clock_timestamp()+interval '60 seconds' WHERE payment_id=?",
                        payment,
                        payment);
            } catch (RuntimeException failure) {
                jdbc.update(
                        "UPDATE stripe_local_expiry_request SET attempts=attempts+1,next_attempt_at=clock_timestamp()+interval '60 seconds' WHERE payment_id=?",
                        payment);
                payments.assignReview(
                        payment, "local-operator", java.time.Instant.now().plusSeconds(900));
            }
        }
    }
}
