package com.blocbuy.order.model;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.order.exception.OrderErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order aggregate root — the system of record for one paid deal participation. Carries a product
 * snapshot (title/image/price) frozen at creation so later catalog edits never rewrite purchase
 * history. References the backing payment by id only — the Payment aggregate never crosses this
 * domain boundary. Monetary amounts are {@code BigDecimal} major units with an explicit currency.
 *
 * <p>Rules: state transitions go through behavioral methods that validate the current state (no
 * public {@code setStatus}); no Lombok {@code @Data}; immutable-snapshot style transitions follow
 * the {@code GroupDeal} aggregate idiom. The order is created only after a verified payment, inside
 * the paid-participation transaction — never from a browser call.
 */
public final class Order {

    private final UUID id;
    private final NewOrderTerms placement;
    private final BigDecimal totalAmount;
    private final OrderStatus status;
    private final Instant paidTime;
    private final Instant cancelTime;
    private final Instant createTime;
    private final int version;

    /** Builds an immutable snapshot; structural checks live in the require* helpers. */
    private Order(UUID id, NewOrderTerms placement, BigDecimal totalAmount, Lifecycle lifecycle) {
        requirePlacement(placement);
        requireIdentity(id, placement);
        requireAmount(totalAmount, placement);
        requireLifecycle(lifecycle);
        this.id = id;
        this.placement = placement;
        this.totalAmount = totalAmount;
        this.status = lifecycle.status();
        this.paidTime = lifecycle.paidTime();
        this.cancelTime = lifecycle.cancelTime();
        this.createTime = lifecycle.createTime();
        this.version = lifecycle.version();
    }

    /** Immutable placement terms for a verified payment; the aggregate computes the total. */
    public record NewOrderTerms(
            String orderNo,
            UUID paymentId,
            UUID dealId,
            UUID userId,
            UUID productId,
            String productTitle,
            String productImage,
            int quantity,
            BigDecimal unitPrice,
            String currency) {}

    /** Lifecycle state of a stored order; reconstitution hands it over as one value. */
    public record Lifecycle(
            OrderStatus status,
            Instant paidTime,
            Instant cancelTime,
            Instant createTime,
            int version) {}

    /**
     * Creates a pending order inside the paid-participation transaction. The id, order number and
     * product snapshot come from the caller (system-owned); {@code paidAt} is the verified payment
     * instant, which also stamps {@code createTime}.
     */
    public static Order create(NewOrderTerms terms, Instant paidAt) {
        if (terms == null) {
            throw new IllegalArgumentException("terms must not be null");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("paidAt must not be null");
        }
        return new Order(
                UuidV7.next(),
                terms,
                terms.unitPrice().multiply(BigDecimal.valueOf(terms.quantity())),
                new Lifecycle(OrderStatus.PENDING, paidAt, null, paidAt, 0));
    }

    /** Reconstitution factory for the infrastructure converter; all fields come from the row. */
    public static Order reconstitute(
            NewOrderTerms placement, UUID id, BigDecimal totalAmount, Lifecycle lifecycle) {
        return new Order(id, placement, totalAmount, lifecycle);
    }

    /** PENDING → CONFIRMED when the team succeeds; terminal states never re-enter. */
    public Order confirm() {
        if (!status.canTransitionTo(OrderStatus.CONFIRMED)) {
            throw new BizException(OrderErrorCode.ORDER_STATE_INVALID);
        }
        return copyWith(OrderStatus.CONFIRMED, cancelTime, version);
    }

    /** PENDING → CANCELLED when the team expires; the instant becomes the durable cancel time. */
    public Order cancel(Instant canceledAt) {
        if (canceledAt == null) {
            throw new IllegalArgumentException("canceledAt must not be null");
        }
        if (!status.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new BizException(OrderErrorCode.ORDER_STATE_INVALID);
        }
        return copyWith(OrderStatus.CANCELLED, canceledAt, version);
    }

    /** Snapshot after a successful compare-and-swap write; the repository bumped the version. */
    public Order withNextVersion() {
        return copyWith(status, cancelTime, version + 1);
    }

    private Order copyWith(OrderStatus nextStatus, Instant nextCancelTime, int nextVersion) {
        return new Order(
                id,
                placement,
                totalAmount,
                new Lifecycle(nextStatus, paidTime, nextCancelTime, createTime, nextVersion));
    }

    private static void requirePlacement(NewOrderTerms placement) {
        if (placement == null) {
            throw new IllegalArgumentException("terms must not be null");
        }
        if (placement.orderNo() == null || placement.orderNo().isBlank()) {
            throw new IllegalArgumentException("orderNo must not be blank");
        }
        if (placement.productTitle() == null || placement.productTitle().isBlank()) {
            throw new IllegalArgumentException("productTitle must not be blank");
        }
        if (placement.quantity() < 1) {
            throw new IllegalArgumentException(
                    "quantity must be >= 1, got " + placement.quantity());
        }
        if (placement.unitPrice() == null || placement.unitPrice().signum() <= 0) {
            throw new IllegalArgumentException("unitPrice must be positive");
        }
        if (placement.currency() == null || placement.currency().isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }

    private static void requireIdentity(UUID id, NewOrderTerms placement) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (placement.paymentId() == null) {
            throw new IllegalArgumentException("paymentId must not be null");
        }
        if (placement.dealId() == null) {
            throw new IllegalArgumentException("dealId must not be null");
        }
        if (placement.userId() == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (placement.productId() == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
    }

    /** The stored total is always the computed one — corrupted rows fail fast on read. */
    private static void requireAmount(BigDecimal totalAmount, NewOrderTerms placement) {
        BigDecimal computed =
                placement.unitPrice().multiply(BigDecimal.valueOf(placement.quantity()));
        if (totalAmount == null || totalAmount.compareTo(computed) != 0) {
            throw new IllegalArgumentException("totalAmount must equal quantity * unitPrice");
        }
    }

    private static void requireLifecycle(Lifecycle lifecycle) {
        if (lifecycle.status() == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (lifecycle.paidTime() == null) {
            throw new IllegalArgumentException("paidTime must not be null");
        }
        if (lifecycle.createTime() == null) {
            throw new IllegalArgumentException("createTime must not be null");
        }
    }

    /** Stable database identity shared by payments, members and receipts. */
    public UUID id() {
        return id;
    }

    /** Business-facing order number; unique among active rows. */
    public String orderNo() {
        return placement.orderNo();
    }

    /** Deduplication key: one order per payment (uq_biz_order_payment_id), by id only. */
    public UUID paymentId() {
        return placement.paymentId();
    }

    /** Group deal this order belongs to; drives the confirm/cancel-by-deal sweeps. */
    public UUID dealId() {
        return placement.dealId();
    }

    /** Buyer reference; My Orders is scoped to it. */
    public UUID userId() {
        return placement.userId();
    }

    /** Ordered product identity frozen at creation. */
    public UUID productId() {
        return placement.productId();
    }

    /** Snapshot of the product title at order time. */
    public String productTitle() {
        return placement.productTitle();
    }

    /** Snapshot of the product image at order time; may be null. */
    public String productImage() {
        return placement.productImage();
    }

    /** Number of units purchased; single-item groupbuy model keeps this at 1 for now. */
    public int quantity() {
        return placement.quantity();
    }

    /** Deal unit price at order time, major units. */
    public BigDecimal unitPrice() {
        return placement.unitPrice();
    }

    /** {@code quantity * unitPrice}, computed at creation and revalidated on reconstruction. */
    public BigDecimal totalAmount() {
        return totalAmount;
    }

    /** ISO-4217 code of {@link #unitPrice()} and {@link #totalAmount()}. */
    public String currency() {
        return placement.currency();
    }

    /** Lifecycle state; transitions are validated by {@link OrderStatus#canTransitionTo}. */
    public OrderStatus status() {
        return status;
    }

    /** Instant the backing payment succeeded. */
    public Instant paidTime() {
        return paidTime;
    }

    /** Instant the order was cancelled (team expiry); null unless cancelled. */
    public Instant cancelTime() {
        return cancelTime;
    }

    /** Row creation instant, also the My Orders sort key. */
    public Instant createTime() {
        return createTime;
    }

    /** Optimistic-lock token; compared and incremented by the repository on state transitions. */
    public int version() {
        return version;
    }
}
