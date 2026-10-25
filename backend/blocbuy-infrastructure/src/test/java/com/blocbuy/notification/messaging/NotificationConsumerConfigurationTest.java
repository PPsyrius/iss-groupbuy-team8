package com.blocbuy.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.blocbuy.notification.event.NotificationEventHandler;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import software.amazon.awssdk.regions.Region;
import tools.jackson.databind.json.JsonMapper;

class NotificationConsumerConfigurationTest {
    private final NotificationConsumerConfiguration configuration =
            new NotificationConsumerConfiguration();

    @Test
    void enabledConsumerWithoutAQueueUrlRefusesToStart() {
        NotificationConsumerProperties settings = defaults();
        IntegrationEventDecoder decoder =
                configuration.integrationEventDecoder(JsonMapper.builder().build());
        NotificationEventHandler handler = mock(NotificationEventHandler.class);
        assertThatThrownBy(
                        () ->
                                configuration.sqsNotificationEventConsumer(
                                        settings, decoder, handler, " ", "us-east-1", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blocbuy.events.sqs.queue-url");
    }

    @Test
    void buildsAnOwnedClientWithTheExplicitRegionAndEndpoint() {
        var consumer =
                configuration.sqsNotificationEventConsumer(
                        defaults(),
                        configuration.integrationEventDecoder(JsonMapper.builder().build()),
                        mock(NotificationEventHandler.class),
                        "http://localhost:9324/000000000000/notification-events",
                        "us-east-1",
                        "http://localhost:9324");
        assertThat(consumer.isRunning()).isFalse();
        consumer.destroy();

        try (var client =
                NotificationConsumerConfiguration.sqsClient(
                        Duration.ofSeconds(20), "eu-west-1", "")) {
            assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.EU_WEST_1);
            var timeouts = client.serviceClientConfiguration().overrideConfiguration();
            assertThat(timeouts.apiCallAttemptTimeout()).contains(Duration.ofSeconds(30));
            assertThat(timeouts.apiCallTimeout()).contains(Duration.ofSeconds(45));
        }
    }

    @Test
    void propertiesRejectValuesOutsideTheSqsLimits() {
        Duration s = Duration.ofSeconds(1);
        List<Runnable> invalid =
                List.of(
                        () -> properties(null, 1, 10, s, s, s, s),
                        () -> properties(" ", 1, 10, s, s, s, s),
                        () -> properties("x".repeat(65), 1, 10, s, s, s, s),
                        () -> properties("n", 0, 10, s, s, s, s),
                        () -> properties("n", 65, 10, s, s, s, s),
                        () -> properties("n", 1, 0, s, s, s, s),
                        () -> properties("n", 1, 11, s, s, s, s),
                        () -> properties("n", 1, 10, Duration.ofSeconds(21), s, s, s),
                        () -> properties("n", 1, 10, Duration.ZERO, s, s, s),
                        () -> properties("n", 1, 10, Duration.ofMillis(1500), s, s, s),
                        () -> properties("n", 1, 10, s, Duration.ofMillis(1500), s, s),
                        () -> properties("n", 1, 10, null, s, s, s),
                        () -> properties("n", 1, 10, s, Duration.ZERO, s, s),
                        () -> properties("n", 1, 10, s, s, Duration.ofMinutes(6), s),
                        () -> properties("n", 1, 10, s, s, s, Duration.ZERO));
        invalid.forEach(
                construct ->
                        assertThatThrownBy(construct::run)
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageStartingWith("blocbuy.events.consumer: "));
        assertThat(properties("n", 64, 10, s, s, Duration.ZERO, s).concurrency()).isEqualTo(64);
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void blankRegionUsesTheSdkDefaultRegionChainWithoutContactingAws() {
        String previous = System.getProperty("aws.region");
        System.setProperty("aws.region", "ap-southeast-1");
        try (var client =
                NotificationConsumerConfiguration.sqsClient(Duration.ofSeconds(1), "", "")) {
            assertThat(client.serviceClientConfiguration().region())
                    .isEqualTo(Region.AP_SOUTHEAST_1);
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
        } finally {
            if (previous == null) {
                System.clearProperty("aws.region");
            } else {
                System.setProperty("aws.region", previous);
            }
        }
    }

    private static NotificationConsumerProperties defaults() {
        return new NotificationConsumerProperties(
                true,
                "notification",
                4,
                10,
                Duration.ofSeconds(20),
                Duration.ofSeconds(60),
                Duration.ofSeconds(15),
                Duration.ofSeconds(5));
    }

    private static NotificationConsumerProperties properties(
            String name,
            int concurrency,
            int maxMessages,
            Duration wait,
            Duration visibility,
            Duration shutdown,
            Duration backoff) {
        return new NotificationConsumerProperties(
                true, name, concurrency, maxMessages, wait, visibility, shutdown, backoff);
    }
}
