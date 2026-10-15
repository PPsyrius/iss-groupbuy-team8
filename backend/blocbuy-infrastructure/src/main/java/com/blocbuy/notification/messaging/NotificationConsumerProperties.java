package com.blocbuy.notification.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code blocbuy.events.consumer.*}: the notification SQS consumer (backend ADR 0007, #446). The
 * queue URL, Region and endpoint are shared with the relay under {@code blocbuy.events.*}.
 *
 * @param enabled master switch, off by default; the SWE5006 EC2 stack runs no broker
 * @param name ledger key in {@code sys_consumed_message.consumer}
 * @param concurrency maximum messages handled at once; nothing more is received until one ends
 * @param maxMessages per receive call (SQS maximum 10)
 * @param waitTime long-poll wait, 1-20 whole seconds
 * @param visibilityTimeout requested on every receive; a failed message reappears after it
 * @param shutdownTimeout how long stopping waits for in-flight messages before releasing them
 * @param errorBackoff pause after a failed receive call, so an outage cannot hot-loop
 */
@ConfigurationProperties(prefix = "blocbuy.events.consumer")
public record NotificationConsumerProperties(
        boolean enabled,
        @DefaultValue("notification") String name,
        @DefaultValue("4") int concurrency,
        @DefaultValue("10") int maxMessages,
        @DefaultValue("20s") Duration waitTime,
        @DefaultValue("60s") Duration visibilityTimeout,
        @DefaultValue("15s") Duration shutdownTimeout,
        @DefaultValue("5s") Duration errorBackoff) {

    public NotificationConsumerProperties {
        require(name != null && !name.isBlank() && name.length() <= 64, "name is 1-64 characters");
        require(concurrency >= 1 && concurrency <= 64, "concurrency is 1-64");
        require(maxMessages >= 1 && maxMessages <= 10, "max-messages is 1-10");
        // Whole seconds (SQS takes seconds); a wait of at least 1 s keeps empty polls from
        // spinning.
        require(
                within(waitTime, Duration.ofSeconds(1), Duration.ofSeconds(20)) && whole(waitTime),
                "wait-time is 1-20 whole seconds");
        require(
                within(visibilityTimeout, Duration.ofSeconds(1), Duration.ofHours(12))
                        && whole(visibilityTimeout),
                "visibility-timeout is 1s-12h in whole seconds");
        require(
                within(shutdownTimeout, Duration.ZERO, Duration.ofMinutes(5)),
                "shutdown-timeout is 0-5m");
        require(
                within(errorBackoff, Duration.ofMillis(1), Duration.ofMinutes(5)),
                "error-backoff is 1ms-5m");
    }

    private static boolean whole(Duration value) {
        return value.toMillis() % 1000 == 0;
    }

    private static boolean within(Duration value, Duration min, Duration max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    private static void require(boolean valid, String rule) {
        if (!valid) {
            throw new IllegalArgumentException("blocbuy.events.consumer: " + rule);
        }
    }
}
