package com.blocbuy.bootstrap.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Experimental team-only partitioning; unrelated expiry and refund jobs remain unchanged. */
@ConfigurationProperties(
        prefix = "blocbuy.scheduling.team-expiry.sharding",
        ignoreUnknownFields = false)
public record TeamExpiryShardingProperties(@DefaultValue("1") int count) {
    static final int MAX_SHARD_COUNT = 6;

    /** Invalid partition configurations fail startup, never silently fall back to a global job. */
    public TeamExpiryShardingProperties {
        if (count < 1 || count > MAX_SHARD_COUNT) {
            throw new IllegalArgumentException(
                    "team-expiry.sharding.count must be between 1 and 6");
        }
    }
}
