package com.blocbuy.catalog.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** Persistence-only policy revision. */
@Data
public class TeamPricePolicyDO {
    private UUID productId;
    private BigDecimal amount;
    private String currency;
    private Instant validFrom;
    private Instant validUntil;
    private int version;
}
