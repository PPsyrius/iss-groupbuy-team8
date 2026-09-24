package com.blocbuy.order.persistence;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.order.exception.OrderErrorCode;
import com.blocbuy.order.model.PurchaseIntent;
import com.blocbuy.order.repository.PurchaseIntentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PurchaseIntentRepositoryImpl implements PurchaseIntentRepository {

    private final BizPurchaseIntentMapper intents;

    @Override
    public Instant currentTime() {
        return intents.currentTime();
    }

    @Override
    public PurchaseIntent save(PurchaseIntent intent) {
        intents.insert(PurchaseIntentConverter.toRow(intent));
        return intent;
    }

    @Override
    public Optional<PurchaseIntent> findById(UUID id) {
        return Optional.ofNullable(PurchaseIntentConverter.toDomain(intents.selectById(id)));
    }

    @Override
    public Optional<PurchaseIntent> lockById(UUID id) {
        return Optional.ofNullable(PurchaseIntentConverter.toDomain(intents.lockById(id)));
    }

    @Override
    public PurchaseIntent markFulfilled(PurchaseIntent intent, UUID paymentId) {
        PurchaseIntent claimed = intent.fulfilBy(paymentId);
        if (claimed == intent) {
            return intent; // same payment replaying its own fulfilment; the row already says so
        }
        int rows = intents.markFulfilled(intent.id(), paymentId, intent.version());
        BizPurchaseIntentDO current = intents.selectById(intent.id());
        if (rows == 0) {
            if (current == null) {
                throw new BizException(OrderErrorCode.PURCHASE_INTENT_NOT_FOUND);
            }
            if (current.getFulfilledPaymentId() == null) {
                throw new IllegalStateException(
                        "Purchase intent claim lost a race it should not have entered");
            }
            if (!paymentId.equals(current.getFulfilledPaymentId())) {
                throw new BizException(OrderErrorCode.PURCHASE_INTENT_ALREADY_FULFILLED);
            }
        }
        // Re-read rather than returning the in-memory snapshot: the row bumped version.
        return PurchaseIntentConverter.toDomain(current);
    }
}
