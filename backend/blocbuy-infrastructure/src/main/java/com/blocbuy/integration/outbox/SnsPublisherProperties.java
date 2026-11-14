package com.blocbuy.integration.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** SNS-only network concurrency; SQS and business transactions remain sequential. */
@ConfigurationProperties("blocbuy.events.sns")
public record SnsPublisherProperties(@DefaultValue("1") int publishConcurrency) {

    /** Rejects invalid concurrency before an enabled relay can send anything. */
    public SnsPublisherProperties {
        if (publishConcurrency < 1 || publishConcurrency > 8) {
            throw new IllegalArgumentException(
                    "blocbuy.events.sns.publish-concurrency must be between 1 and 8");
        }
    }
}
