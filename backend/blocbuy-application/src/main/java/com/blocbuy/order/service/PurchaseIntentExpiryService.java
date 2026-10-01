package com.blocbuy.order.service;

import com.blocbuy.groupbuy.service.GroupDealSettlementSupport;
import com.blocbuy.order.repository.PurchaseIntentRepository;
import com.blocbuy.payment.service.PaymentIntentExpirySupport;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One short transaction, team then intent; a settling intent is skipped rather than modified. */
@Service
@RequiredArgsConstructor
public class PurchaseIntentExpiryService {
    private final PurchaseIntentRepository intents;
    private final GroupDealSettlementSupport teams;
    private final PaymentIntentExpirySupport payments;

    public enum Outcome {
        EXPIRED,
        SKIPPED,
        PAID
    }

    @Transactional(timeout = 10)
    public Outcome expireOne(UUID id) {
        var preliminary = intents.findById(id);
        if (preliminary.isEmpty()) {
            return Outcome.SKIPPED;
        }
        teams.lockTeam(preliminary.orElseThrow().dealId());
        var locked = intents.tryLockForExpiry(id);
        if (locked.isEmpty()) {
            return Outcome.SKIPPED;
        }
        var before = locked.orElseThrow();
        var after = before.expireAt(intents.currentTime());
        if (after == before) {
            return Outcome.SKIPPED;
        }
        if (payments.hasSucceededIntent(id)) {
            return Outcome.PAID;
        }
        intents.markExpired(before, after);
        return Outcome.EXPIRED;
    }
}
