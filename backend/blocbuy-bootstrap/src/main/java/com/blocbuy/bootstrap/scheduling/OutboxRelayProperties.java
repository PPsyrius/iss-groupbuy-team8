package com.blocbuy.bootstrap.scheduling;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Bounded relay pacing; the batch size, initial delay and renewable lease do not change. */
@ConfigurationProperties("blocbuy.events.relay")
public record OutboxRelayProperties(@DefaultValue("PT2S") Duration interval) {

    /** Rejects pacing that could silently disable the relay or create an unbounded hot loop. */
    public OutboxRelayProperties {
        if (interval == null
                || interval.compareTo(Duration.ofMillis(100)) < 0
                || interval.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException(
                    "blocbuy.events.relay.interval must be between PT0.1S and PT1M");
        }
    }
}
