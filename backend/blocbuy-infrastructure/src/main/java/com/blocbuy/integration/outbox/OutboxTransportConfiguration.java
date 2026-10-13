package com.blocbuy.integration.outbox;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/** Provider clients are created only when the outbox relay is explicitly enabled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
        "${blocbuy.events.enabled:false} and ${blocbuy.events.relay.enabled:false}")
public class OutboxTransportConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "blocbuy.events.transport", havingValue = "sqs")
    SqsClient outboxSqsClient(
            @Value("${blocbuy.events.region:us-east-1}") String region,
            @Value("${blocbuy.events.sqs.endpoint:}") String endpoint) {
        var builder =
                SqsClient.builder()
                        .region(Region.of(region))
                        .credentialsProvider(DefaultCredentialsProvider.builder().build());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "blocbuy.events.transport", havingValue = "sqs")
    SqsOutboxEventTransport sqsOutboxEventTransport(
            SqsClient client,
            @Value("${blocbuy.events.sqs.queue-url}") String queueUrl,
            tools.jackson.databind.ObjectMapper json) {
        return new SqsOutboxEventTransport(client, queueUrl, json);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "blocbuy.events.transport", havingValue = "sns")
    SnsClient outboxSnsClient(@Value("${blocbuy.events.region}") String region) {
        return SnsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "blocbuy.events.transport", havingValue = "sns")
    SnsOutboxEventTransport snsOutboxEventTransport(
            SnsClient client,
            @Value("${blocbuy.events.sns.topic-arn}") String topicArn,
            tools.jackson.databind.ObjectMapper json) {
        return new SnsOutboxEventTransport(client, topicArn, json);
    }
}
