package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class OutboxTransportConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            OutboxTransportConfiguration.class, JsonConfiguration.class);

    @Test
    void createsOnlyTheConfiguredSqsTransportWhenRelayIsEnabled() {
        runner.withPropertyValues(
                        "blocbuy.events.enabled=true",
                        "blocbuy.events.relay.enabled=true",
                        "blocbuy.events.transport=sqs",
                        "blocbuy.events.region=us-east-1",
                        "blocbuy.events.sqs.endpoint=http://localhost:9324",
                        "blocbuy.events.sqs.queue-url=http://localhost:9324/queue")
                .run(context -> {
                    assertThat(context).hasSingleBean(SqsClient.class);
                    assertThat(context).hasSingleBean(SqsOutboxEventTransport.class);
                    assertThat(context).doesNotHaveBean(SnsClient.class);
                    assertTimeouts(
                            context.getBean(SqsClient.class)
                                    .serviceClientConfiguration()
                                    .overrideConfiguration());
                });
    }

    @Test
    void buildsSqsClientWithoutAnEndpointOverrideForAws() {
        runner.withPropertyValues(
                        "blocbuy.events.enabled=true",
                        "blocbuy.events.relay.enabled=true",
                        "blocbuy.events.transport=sqs",
                        "blocbuy.events.region=ap-southeast-1",
                        "blocbuy.events.sqs.endpoint=",
                        "blocbuy.events.sqs.queue-url=https://sqs.us-east-1.amazonaws.com/123/events")
                .run(context -> assertThat(context).hasSingleBean(SqsClient.class));
    }

    @Test
    void createsOnlyTheConfiguredSnsTransportWhenRelayIsEnabled() {
        runner.withPropertyValues(
                        "blocbuy.events.enabled=true",
                        "blocbuy.events.relay.enabled=true",
                        "blocbuy.events.transport=sns",
                        "blocbuy.events.region=us-east-1",
                        "blocbuy.events.sns.topic-arn=arn:aws:sns:us-east-1:000000000000:events")
                .run(context -> {
                    assertThat(context).hasSingleBean(SnsClient.class);
                    assertThat(context).hasSingleBean(SnsOutboxEventTransport.class);
                    assertThat(context).doesNotHaveBean(SqsClient.class);
                    assertTimeouts(
                            context.getBean(SnsClient.class)
                                    .serviceClientConfiguration()
                                    .overrideConfiguration());
                });
    }

    @Test
    void doesNotCreateProviderClientsWhileRelayIsDisabled() {
        runner.withPropertyValues(
                        "blocbuy.events.enabled=true",
                        "blocbuy.events.relay.enabled=false",
                        "blocbuy.events.transport=sqs")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SqsClient.class);
                    assertThat(context).doesNotHaveBean(SnsClient.class);
                });
    }

    @Test
    void usesTheSdkDefaultRegionChainWhenNoRegionIsConfigured() {
        String previous = System.getProperty("aws.region");
        System.setProperty("aws.region", "ap-southeast-1");
        try {
            runner.withPropertyValues(
                            "blocbuy.events.enabled=true",
                            "blocbuy.events.relay.enabled=true",
                            "blocbuy.events.transport=sns",
                            "blocbuy.events.region=",
                            "blocbuy.events.sns.topic-arn=arn:aws:sns:ap-southeast-1:000000000000:e")
                    .run(context ->
                            assertThat(
                                            context.getBean(SnsClient.class)
                                                    .serviceClientConfiguration()
                                                    .region())
                                    .isEqualTo(Region.AP_SOUTHEAST_1));
        } finally {
            if (previous == null) {
                System.clearProperty("aws.region");
            } else {
                System.setProperty("aws.region", previous);
            }
        }
    }

    @Test
    void refusesToStartWithoutATopicArn() {
        runner.withPropertyValues(
                        "blocbuy.events.enabled=true",
                        "blocbuy.events.relay.enabled=true",
                        "blocbuy.events.transport=sns",
                        "blocbuy.events.region=ap-southeast-1",
                        "blocbuy.events.sns.topic-arn=")
                .run(context ->
                        assertThat(context)
                                .getFailure()
                                .hasRootCauseMessage(
                                        "blocbuy.events.sns.topic-arn must be set when the"
                                                + " outbox relay is enabled"));
    }

    @Test
    void refusesToStartWithoutAQueueUrl() {
        runner.withPropertyValues(
                        "blocbuy.events.enabled=true",
                        "blocbuy.events.relay.enabled=true",
                        "blocbuy.events.transport=sqs",
                        "blocbuy.events.region=us-east-1")
                .run(context ->
                        assertThat(context)
                                .getFailure()
                                .hasRootCauseMessage(
                                        "blocbuy.events.sqs.queue-url must be set when the"
                                                + " outbox relay is enabled"));
    }

    @Test
    void refusesToStartWithAnUnknownTransport() {
        runner.withPropertyValues(
                        "blocbuy.events.enabled=true",
                        "blocbuy.events.relay.enabled=true",
                        "blocbuy.events.transport=kafka")
                .run(context ->
                        assertThat(context)
                                .getFailure()
                                .hasRootCauseMessage(
                                        "blocbuy.events.transport must be sqs or sns when"
                                                + " the outbox relay is enabled, not"
                                                + " 'kafka'"));
    }

    private static void assertTimeouts(
            software.amazon.awssdk.core.client.config.ClientOverrideConfiguration configuration) {
        assertThat(configuration.apiCallTimeout()).contains(java.time.Duration.ofSeconds(15));
        assertThat(configuration.apiCallAttemptTimeout()).contains(java.time.Duration.ofSeconds(5));
    }

    @Configuration(proxyBeanMethods = false)
    static class JsonConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }
}
