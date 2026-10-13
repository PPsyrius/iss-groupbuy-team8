package com.blocbuy.integration.outbox;

import java.net.URI;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Provider clients are created only when the outbox relay is explicitly enabled (backend ADR 0007).
 * An enabled relay without a usable target fails startup instead of failing every send and moving
 * committed events to {@code failed_at} after its retry budget.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
        "${blocbuy.events.enabled:false} and ${blocbuy.events.relay.enabled:false}")
public class OutboxTransportConfiguration {

    static final String SQS = "sqs";
    static final String SNS = "sns";

    @Bean
    InitializingBean outboxTransportCheck(@Value("${blocbuy.events.transport:}") String transport) {
        return () -> {
            if (!SQS.equals(transport) && !SNS.equals(transport)) {
                throw new IllegalStateException(
                        "blocbuy.events.transport must be sqs or sns when the outbox relay is"
                                + " enabled, not '"
                                + transport
                                + "'");
            }
        };
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "blocbuy.events.transport", havingValue = SQS)
    SqsClient outboxSqsClient(
            @Value("${blocbuy.events.region:}") String region,
            @Value("${blocbuy.events.sqs.endpoint:}") String endpoint) {
        var builder =
                withRegion(SqsClient.builder(), region)
                        .credentialsProvider(DefaultCredentialsProvider.builder().build());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "blocbuy.events.transport", havingValue = SQS)
    SqsOutboxEventTransport sqsOutboxEventTransport(
            SqsClient client,
            @Value("${blocbuy.events.sqs.queue-url:}") String queueUrl,
            tools.jackson.databind.ObjectMapper json) {
        return new SqsOutboxEventTransport(
                client, required("blocbuy.events.sqs.queue-url", queueUrl), json);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "blocbuy.events.transport", havingValue = SNS)
    SnsClient outboxSnsClient(@Value("${blocbuy.events.region:}") String region) {
        return withRegion(SnsClient.builder(), region)
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "blocbuy.events.transport", havingValue = SNS)
    SnsOutboxEventTransport snsOutboxEventTransport(
            SnsClient client,
            @Value("${blocbuy.events.sns.topic-arn:}") String topicArn,
            tools.jackson.databind.ObjectMapper json) {
        return new SnsOutboxEventTransport(
                client, required("blocbuy.events.sns.topic-arn", topicArn), json);
    }

    /**
     * An explicit {@code blocbuy.events.region}, otherwise the SDK default region chain ({@code
     * AWS_REGION} on ECS); never a guessed default Region.
     */
    static <B extends AwsClientBuilder<B, ?>> B withRegion(B builder, String region) {
        if (!region.isBlank()) {
            builder.region(Region.of(region));
        }
        return builder;
    }

    static String required(String property, String value) {
        if (value.isBlank()) {
            throw new IllegalStateException(
                    property + " must be set when the outbox relay is enabled");
        }
        return value;
    }
}
