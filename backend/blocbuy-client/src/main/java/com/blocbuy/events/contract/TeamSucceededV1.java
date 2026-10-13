package com.blocbuy.events.contract;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Public integration payload for a team that reached its target size. */
public record TeamSucceededV1(UUID dealId, UUID productId, int size, List<UUID> recipientUserIds) {

    public TeamSucceededV1 {
        Objects.requireNonNull(dealId);
        Objects.requireNonNull(productId);
        if (size < 2) throw new IllegalArgumentException("size must be at least two");
        recipientUserIds = List.copyOf(Objects.requireNonNull(recipientUserIds));
    }
}
