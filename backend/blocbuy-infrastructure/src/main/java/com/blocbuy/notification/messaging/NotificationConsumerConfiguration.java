package com.blocbuy.notification.messaging;

import com.blocbuy.notification.event.NotificationEventHandler;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the notification SQS consumer only when {@code blocbuy.events.consumer.enabled=true}. It
 * lives in a notification package, so the monolith and notification-svc scan it while commerce-svc
 * and identity-svc never do. An enabled consumer without a queue URL refuses to start.
 *
 * <p>The consumer owns its {@link SqsClient} instead of publishing a bean: the relay's client has a
 * 15 s call timeout (shorter than a 20 s long poll), and a second {@code SqsClient} bean would make
 * the relay's injection ambiguous in the monolith.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "blocbuy.events.consumer.enabled", havingValue = "true")
@EnableConfigurationProperties(NotificationConsumerProperties.class)
public class NotificationConsumerConfiguration {

    @Bean
    IntegrationEventDecoder integrationEventDecoder(ObjectMapper json) {
        return new IntegrationEventDecoder(json);
    }

    @Bean
    SqsNotificationEventConsumer sqsNotificationEventConsumer(
            NotificationConsumerProperties properties,
            IntegrationEventDecoder decoder,
            NotificationEventHandler handler,
            @Value("${blocbuy.events.sqs.queue-url:}") String queueUrl,
            @Value("${blocbuy.events.region:}") String region,
            @Value("${blocbuy.events.sqs.endpoint:}") String endpoint) {
        if (queueUrl.isBlank()) {
            throw new IllegalStateException(
                    "blocbuy.events.sqs.queue-url must be set when the notification consumer is"
                            + " enabled");
        }
        return new SqsNotificationEventConsumer(
                sqsClient(properties.waitTime(), region, endpoint),
                queueUrl,
                decoder,
                handler,
                properties);
    }

    /**
     * An explicit {@code blocbuy.events.region}, otherwise the SDK default chain ({@code
     * AWS_REGION} on ECS). Timeouts leave room for the long poll plus network latency.
     */
    static SqsClient sqsClient(Duration waitTime, String region, String endpoint) {
        var builder =
                SqsClient.builder()
                        .credentialsProvider(DefaultCredentialsProvider.builder().build())
                        .overrideConfiguration(
                                ClientOverrideConfiguration.builder()
                                        .apiCallAttemptTimeout(waitTime.plusSeconds(10))
                                        .apiCallTimeout(waitTime.plusSeconds(25))
                                        .build());
        if (!region.isBlank()) {
            builder.region(Region.of(region));
        }
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
