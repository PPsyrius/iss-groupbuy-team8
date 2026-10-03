package com.blocbuy.groupbuy.service;

import com.blocbuy.groupbuy.event.MemberJoined;
import com.blocbuy.groupbuy.event.TeamExpired;
import com.blocbuy.groupbuy.event.TeamSucceeded;
import com.blocbuy.groupbuy.gateway.OpenDealOfferCache;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Deletes derived listing offers only after the database fact commits. No admission dependency. */
@Component
@RequiredArgsConstructor
public class OpenDealOfferCacheSubscriber {
    private final OpenDealOfferCache cache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberJoined(MemberJoined event) {
        cache.evict(List.of(event.productId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamSucceeded(TeamSucceeded event) {
        cache.evict(List.of(event.productId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamExpired(TeamExpired event) {
        cache.evict(List.of(event.productId()));
    }
}
