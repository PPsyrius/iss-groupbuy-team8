package com.blocbuy.bootstrap.fixture;

import com.blocbuy.groupbuy.model.NewDealTerms;
import com.blocbuy.groupbuy.model.PaidParticipation;
import com.blocbuy.groupbuy.service.PaidGroupDealService;
import com.blocbuy.payment.event.PaymentSucceeded;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.model.CheckoutQuote;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Test-only business collaborator. Never packaged or registered by the application. */
public final class PaymentBusinessFixture
        implements PaymentBusinessPort, com.blocbuy.payment.gateway.PaymentIntentPort {
    private final JdbcTemplate jdbc;
    private final PaidGroupDealService groups;

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Constructor-injected shared Spring JDBC collaborator; intentionally not copied")
    public PaymentBusinessFixture(JdbcTemplate jdbc, PaidGroupDealService groups) {
        this.jdbc = jdbc;
        this.groups = groups;
    }

    /**
     * Only IDs enter here; prices and group terms come from the database. Caller owns transaction.
     */
    public UUID createIntent(UUID user, String operation, UUID businessId) {
        requireTransaction();
        activeUser(user);
        Map<String, Object> product;
        UUID deal;
        BigDecimal amount;
        int target;
        Instant deadline;
        if ("START".equals(operation)) {
            product =
                    jdbc.queryForMap(
                            "SELECT * FROM biz_product WHERE id=? AND NOT deleted FOR UPDATE",
                            businessId);
            var policy =
                    jdbc.queryForMap(
                            "SELECT * FROM biz_team_price_policy WHERE product_id=? ORDER BY version DESC LIMIT 1",
                            businessId);
            var terms =
                    jdbc.queryForMap(
                            "SELECT * FROM stripe_local_product_terms WHERE product_id=?",
                            businessId);
            Instant now = now();
            if (Boolean.TRUE.equals(policy.get("deleted"))
                    || now.isBefore(time(policy, "valid_from"))
                    || !now.plusSeconds(2400).isBefore(time(policy, "valid_until"))) {
                throw new IllegalArgumentException("No sufficiently valid server price policy");
            }
            amount = (BigDecimal) policy.get("amount");
            if (!"sgd".equals(policy.get("currency"))
                    || amount.signum() <= 0
                    || amount.compareTo((BigDecimal) product.get("original_price")) >= 0) {
                throw new IllegalArgumentException("Invalid server price policy");
            }
            target = (Integer) terms.get("target_size");
            int lifetime = (Integer) terms.get("lifetime_seconds");
            if (target < 2 || lifetime < 2400 || lifetime > 86400) {
                throw new IllegalArgumentException("Invalid local group terms");
            }
            deadline = now.plusSeconds(lifetime);
            deal = UUID.randomUUID();
        } else if ("JOIN".equals(operation)) {
            lockGroup(businessId);
            var group =
                    jdbc.queryForMap(
                            "SELECT * FROM biz_group_deal WHERE id=? AND NOT deleted", businessId);
            if (!"open".equals(group.get("status"))
                    || !now().isBefore(time(group, "expire_time"))
                    || (Integer) group.get("current_size") >= (Integer) group.get("target_size")) {
                throw new IllegalArgumentException("Team is not accepting new checkouts");
            }
            product =
                    jdbc.queryForMap(
                            "SELECT * FROM biz_product WHERE id=? AND NOT deleted",
                            group.get("product_id"));
            amount = (BigDecimal) group.get("deal_price");
            target = (Integer) group.get("target_size");
            deadline = time(group, "expire_time");
            deal = businessId;
        } else {
            throw new IllegalArgumentException("START or JOIN required");
        }
        if (!"on_sale".equals(product.get("status"))
                || (Integer) product.get("stock") < 1
                || !"sgd".equals(product.get("currency"))) {
            throw new IllegalArgumentException("Product unavailable");
        }
        UUID intent = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO stripe_local_intent(id,user_id,operation,deal_id,product_id,product_title,amount,currency,target_size,quote_until,deadline) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                intent,
                user,
                operation,
                deal,
                product.get("id"),
                product.get("title"),
                amount,
                "sgd",
                target,
                Timestamp.from(now().plusSeconds(2400)),
                Timestamp.from(deadline));
        return intent;
    }

    @Override
    public CheckoutQuote lockQuote(UUID user, UUID intent) {
        var preliminary = intent(intent);
        lockGroup((UUID) preliminary.get("deal_id"));
        var stored = lockedIntent(intent);
        activeUser(user);
        if (!user.equals(stored.get("user_id")) || stored.get("fulfilled_payment_id") != null) {
            throw new IllegalArgumentException("Intent unavailable for this buyer");
        }
        if ("JOIN".equals(stored.get("operation"))) {
            UUID deal = (UUID) stored.get("deal_id");
            boolean eligible =
                    Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM biz_group_deal g WHERE g.id=? AND g.status='open' AND NOT g.deleted AND g.expire_time>clock_timestamp() AND g.current_size<g.target_size AND NOT EXISTS(SELECT 1 FROM biz_group_member m WHERE m.deal_id=g.id AND m.user_id=? AND NOT m.deleted))",
                                    Boolean.class,
                                    deal,
                                    user));
            if (!eligible) {
                throw new IllegalArgumentException("Buyer cannot join this team");
            }
        }
        return quote(stored);
    }

    @Override
    public void lockForSettlement(CheckoutQuote quote) {
        var preliminary = intent(quote.intentId());
        lockGroup((UUID) preliminary.get("deal_id"));
        if (!quote.equals(quote(lockedIntent(quote.intentId())))) {
            throw new IllegalStateException("Immutable intent changed");
        }
    }

    @Override
    public Outcome fulfil(PaymentSucceeded event) {
        requireTransaction();
        var quote = event.quote();
        var stored = lockedIntent(quote.intentId());
        if (stored.get("fulfilled_payment_id") != null) {
            if (!event.paymentId().equals(stored.get("fulfilled_payment_id"))) {
                return new Outcome(null, null, "INTENT_ALREADY_FULFILLED");
            }
            UUID order =
                    jdbc.queryForObject(
                            "SELECT id FROM biz_order WHERE payment_id=?",
                            UUID.class,
                            event.paymentId());
            return new Outcome((UUID) stored.get("deal_id"), order, null);
        }
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM sys_user WHERE id=? AND status='active' AND NOT deleted)",
                        Boolean.class,
                        quote.userId()))) {
            return new Outcome(null, null, "BUYER_INACTIVE");
        }
        // Serialize inventory with catalog writers, after group/intent/payment locks.
        var product =
                jdbc.queryForMap(
                        "SELECT * FROM biz_product WHERE id=? FOR UPDATE",
                        stored.get("product_id"));
        if (Boolean.TRUE.equals(product.get("deleted"))
                || !"on_sale".equals(product.get("status"))
                || (Integer) product.get("stock") < 1) {
            return new Outcome(null, null, "PRODUCT_UNAVAILABLE");
        }
        var paid =
                new PaidParticipation(
                        event.paymentId(), quote.userId(), quote.amount(), quote.currency());
        UUID deal = (UUID) stored.get("deal_id");
        var result =
                "START".equals(quote.operation())
                        ? groups.startConfirmed(
                                new NewDealTerms(
                                        deal,
                                        "LOCAL-" + deal,
                                        (UUID) stored.get("product_id"),
                                        quote.amount(),
                                        quote.currency(),
                                        (Integer) stored.get("target_size"),
                                        quote.businessDeadline()),
                                paid)
                        : groups.joinConfirmed(deal, paid);
        if (!"applied".equals(result.outcome())) {
            return new Outcome(deal, null, "GROUP_REJECTED");
        }
        UUID order = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO biz_order(id,order_no,user_id,deal_id,product_id,product_title,quantity,unit_price,total_amount,currency,status,payment_id,paid_time) VALUES(?,?,?,?,?,?,1,?,?,?,'paid',?,clock_timestamp())",
                order,
                "LOCAL-" + order,
                quote.userId(),
                deal,
                stored.get("product_id"),
                stored.get("product_title"),
                quote.amount(),
                quote.amount(),
                quote.currency(),
                event.paymentId());
        requireOne(
                jdbc.update(
                        "UPDATE biz_group_member SET order_id=? WHERE payment_id=? AND deal_id=? AND NOT deleted",
                        order,
                        event.paymentId(),
                        deal));
        requireOne(
                jdbc.update(
                        "UPDATE stripe_local_intent SET fulfilled_payment_id=? WHERE id=? AND fulfilled_payment_id IS NULL",
                        event.paymentId(),
                        quote.intentId()));
        requireOne(
                jdbc.update(
                        "UPDATE biz_product SET stock=stock-1,sold_count=sold_count+1,version=version+1 WHERE id=? AND stock>0",
                        stored.get("product_id")));
        return new Outcome(deal, order, null);
    }

    @Override
    public FulfilmentEvidence fulfilmentEvidence(PaymentSucceeded event) {
        long any =
                java.util.Objects.requireNonNull(
                        jdbc.queryForObject(
                                "SELECT (SELECT count(*) FROM biz_group_member WHERE payment_id=?)+(SELECT count(*) FROM biz_order WHERE payment_id=?)",
                                Long.class,
                                event.paymentId(),
                                event.paymentId()));
        if (any == 0) {
            return FulfilmentEvidence.NONE;
        }
        boolean valid =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM biz_group_member m JOIN biz_order o ON o.id=m.order_id JOIN stripe_local_intent i ON i.fulfilled_payment_id=o.payment_id WHERE o.payment_id=? AND m.payment_id=o.payment_id AND o.user_id=? AND m.user_id=o.user_id AND m.deal_id=o.deal_id AND i.deal_id=o.deal_id AND i.id=? AND o.total_amount=? AND o.currency=? AND o.status='paid' AND m.status='paid' AND NOT o.deleted AND NOT m.deleted)",
                                Boolean.class,
                                event.paymentId(),
                                event.quote().userId(),
                                event.quote().intentId(),
                                event.quote().amount(),
                                event.quote().currency()));
        return valid && any == 2 ? FulfilmentEvidence.COMPLETE : FulfilmentEvidence.INCONSISTENT;
    }

    @Override
    public void lockExpiredTeam(UUID dealId) {
        lockGroup(dealId);
        if (!"expired"
                .equals(
                        jdbc.queryForObject(
                                "SELECT status FROM biz_group_deal WHERE id=?",
                                String.class,
                                dealId))) {
            throw new IllegalStateException("Team is not expired");
        }
    }

    public void lockGroup(UUID deal) {
        requireTransaction();
        // Also serializes a not-yet-created START group; existing team row remains the shared lock.
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text),0))",
                deal.toString());
        jdbc.queryForList("SELECT id FROM biz_group_deal WHERE id=? FOR UPDATE", deal);
    }

    private Map<String, Object> intent(UUID id) {
        return jdbc.queryForMap("SELECT * FROM stripe_local_intent WHERE id=?", id);
    }

    private Map<String, Object> lockedIntent(UUID id) {
        requireTransaction();
        return jdbc.queryForMap("SELECT * FROM stripe_local_intent WHERE id=? FOR UPDATE", id);
    }

    private CheckoutQuote quote(Map<String, Object> row) {
        return new CheckoutQuote(
                (UUID) row.get("id"),
                (UUID) row.get("user_id"),
                (String) row.get("operation"),
                (UUID) row.get("deal_id"),
                (BigDecimal) row.get("amount"),
                (String) row.get("currency"),
                time(row, "quote_until"),
                time(row, "deadline"));
    }

    private void activeUser(UUID id) {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM sys_user WHERE id=? AND status='active' AND NOT deleted)",
                        Boolean.class,
                        id))) {
            throw new IllegalArgumentException("Active local buyer required");
        }
    }

    private Instant now() {
        return java.util.Objects.requireNonNull(
                        jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class))
                .toInstant();
    }

    private static Instant time(Map<String, Object> row, String field) {
        return ((Timestamp) row.get(field)).toInstant();
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Shared database transaction required");
        }
    }

    private static void requireOne(int changed) {
        if (changed != 1) {
            throw new IllegalStateException("Local business write did not affect exactly one row");
        }
    }
}
