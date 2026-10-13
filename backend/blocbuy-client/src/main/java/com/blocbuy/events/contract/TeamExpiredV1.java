package com.blocbuy.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Public integration payload for an expired team. */
public record TeamExpiredV1(
        UUID dealId, UUID productId, Instant expiredAt, List<UUID> recipientUserIds) {

    public TeamExpiredV1 {
        Objects.requireNonNull(dealId);
        Objects.requireNonNull(productId);
        Objects.requireNonNull(expiredAt);
        recipientUserIds = List.copyOf(Objects.requireNonNull(recipientUserIds));
    }
}
