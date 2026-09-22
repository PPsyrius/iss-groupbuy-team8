package com.blocbuy.order.repository;

import com.blocbuy.order.model.PurchaseIntent;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for the PurchaseIntent aggregate (one repository per aggregate root). Implemented
 * in the infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 *
 * <p>Concurrency contract: the checkout and settlement paths take locks in one fixed order — group
 * deal, then intent, then payment. {@link #lockById(UUID)} is the middle step and every caller that
 * intends to write must use it rather than {@link #findById(UUID)}; taking the intent before the
 * group deadlocks against a concurrent JOIN on the same team.
 *
 * <p>One payment consumes at most one intent: {@code uq_biz_purchase_intent_fulfilled_payment}
 * turns a replayed webhook into a database collision rather than a second membership and order.
 */
public interface PurchaseIntentRepository {

    /** Inserts a freshly issued, unfulfilled intent. */
    PurchaseIntent save(PurchaseIntent intent);

    /** Reads without locking; for callers that only report, never for a caller that will write. */
    Optional<PurchaseIntent> findById(UUID id);

    /** {@code SELECT ... FOR UPDATE} on the active row; call after the group deal lock. */
    Optional<PurchaseIntent> lockById(UUID id);

    /**
     * Compare-and-swap that records the payment which consumed the intent.
     *
     * @return the stored snapshot after the write
     * @throws com.blocbuy.common.exception.BizException {@code PURCHASE_INTENT_ALREADY_FULFILLED}
     *     when another payment won the race, {@code PURCHASE_INTENT_NOT_FOUND} when the row is gone
     */
    PurchaseIntent markFulfilled(PurchaseIntent intent, UUID paymentId);
}
