package com.blocbuy.catalog.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** Persistence-only immutable quote row. */
@Data
public class TeamPriceQuoteDO {
    private UUID id;
    private UUID productId;
    private UUID ownerId;
    private int policyVersion;
    private BigDecimal amount;
    private String currency;
    private Instant issuedAt;
    private Instant expiresAt;
}
