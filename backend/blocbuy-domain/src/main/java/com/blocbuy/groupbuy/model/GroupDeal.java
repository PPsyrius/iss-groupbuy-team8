package com.blocbuy.groupbuy.model;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * GroupDeal aggregate root (biz_group_deal). One group-buy campaign against a product; PostgreSQL
 * is the source of truth for deal progress, never Redis.
 *
 * <p>Rules: the aggregate root is the only entry point for mutating its state; state transitions go
 * through behavioral methods that validate the current state (no public {@code setStatus}); no
 * Lombok {@code @Data}; monetary amounts are {@code BigDecimal} major units. Paid transitions
 * return immutable snapshots. The application must lock the stored deal, check membership and
 * payment idempotency, then atomically persist the member and returned snapshot.
 */
public final class GroupDeal {

    private final UUID id;
    private final String dealNo;
    private final UUID productId;
    private final UUID leaderUserId;
    private final BigDecimal dealPrice;
    private final String currency;
    private final int targetSize;
    private final int currentSize;
    private final DealStatus status;
    private final Instant startTime;
    private final Instant expireTime;
    private final Instant successTime;

    private GroupDeal(
            UUID id,
            String dealNo,
            UUID productId,
            UUID leaderUserId,
            BigDecimal dealPrice,
            String currency,
            int targetSize,
            int currentSize,
            DealStatus status,
            Instant startTime,
            Instant expireTime,
            Instant successTime) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (leaderUserId == null) {
            throw new IllegalArgumentException("leaderUserId must not be null");
        }
        if (dealPrice == null || dealPrice.signum() < 0) {
            throw new IllegalArgumentException("dealPrice must not be null or negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (targetSize < 2) {
            throw new IllegalArgumentException("targetSize must be >= 2, got " + targetSize);
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (startTime == null || expireTime == null) {
            throw new IllegalArgumentException("startTime and expireTime must not be null");
        }
        this.id = id;
        this.dealNo = dealNo;
        this.productId = productId;
        this.leaderUserId = leaderUserId;
        this.dealPrice = dealPrice;
        this.currency = currency;
        this.targetSize = targetSize;
        this.currentSize = currentSize;
        this.status = status;
        this.startTime = startTime;
        this.expireTime = expireTime;
        this.successTime = successTime;
    }

    /** Reconstitution factory for the infrastructure converter; all fields come from the row. */
    public static GroupDeal reconstitute(
            UUID id,
            String dealNo,
            UUID productId,
            UUID leaderUserId,
            BigDecimal dealPrice,
            String currency,
            int targetSize,
            int currentSize,
            DealStatus status,
            Instant startTime,
            Instant expireTime,
            Instant successTime) {
        return new GroupDeal(
                id,
                dealNo,
                productId,
                leaderUserId,
                dealPrice,
                currency,
                targetSize,
                currentSize,
                status,
                startTime,
                expireTime,
                successTime);
    }

    static GroupDeal startPaid(NewDealTerms terms, PaidParticipation payment, Instant now) {
        return new GroupDeal(
                terms.id(),
                terms.dealNo(),
                terms.productId(),
                payment.userId(),
                terms.price(),
                terms.currency(),
                terms.targetSize(),
                1,
                DealStatus.OPEN,
                now,
                terms.expireTime(),
                null);
    }

    /**
     * Applies one already-confirmed payment. The membership fact and current time must be read
     * after acquiring the database deal lock; this method is not a concurrency or payment gate.
     */
    public GroupDeal joinPaid(PaidParticipation payment, boolean alreadyMember, Instant now) {
        if (payment == null || now == null) {
            throw new BizException(GroupbuyErrorCode.INVALID_PARTICIPATION);
        }
        payment.requirePrice(dealPrice, currency);
        if (alreadyMember || leaderUserId.equals(payment.userId())) {
            throw new BizException(GroupbuyErrorCode.ALREADY_MEMBER);
        }
        if (status != DealStatus.OPEN || now.isBefore(startTime) || !now.isBefore(expireTime)) {
            throw new BizException(GroupbuyErrorCode.DEAL_CLOSED);
        }
        if (currentSize < 1 || currentSize >= targetSize) {
            throw new BizException(GroupbuyErrorCode.DEAL_FULL);
        }
        int nextSize = currentSize + 1;
        boolean succeeded = nextSize == targetSize;
        return new GroupDeal(
                id,
                dealNo,
                productId,
                leaderUserId,
                dealPrice,
                currency,
                targetSize,
                nextSize,
                succeeded ? DealStatus.SUCCEEDED : DealStatus.OPEN,
                startTime,
                expireTime,
                succeeded ? now : null);
    }

    public UUID id() {
        return id;
    }

    public String dealNo() {
        return dealNo;
    }

    public UUID productId() {
        return productId;
    }

    public UUID leaderUserId() {
        return leaderUserId;
    }

    /** Per-unit group price in major units. */
    public BigDecimal dealPrice() {
        return dealPrice;
    }

    public String currency() {
        return currency;
    }

    public int targetSize() {
        return targetSize;
    }

    public int currentSize() {
        return currentSize;
    }

    public DealStatus status() {
        return status;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant expireTime() {
        return expireTime;
    }

    public Instant successTime() {
        return successTime;
    }
}
