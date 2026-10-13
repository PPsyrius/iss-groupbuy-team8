package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                        "blocbuy.events.sqs.endpoint=http://localhost:9324",
                        "blocbuy.events.sqs.queue-url=http://localhost:9324/queue")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(SqsClient.class);
                            assertThat(context).hasSingleBean(SqsOutboxEventTransport.class);
                            assertThat(context).doesNotHaveBean(SnsClient.class);
                        });
    }

    @Test
    void buildsSqsClientWithoutAnEndpointOverrideForAws() {
        runner.withPropertyValues(
                        "blocbuy.events.enabled=true",
                        "blocbuy.events.relay.enabled=true",
                        "blocbuy.events.transport=sqs",
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
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(SnsClient.class);
                            assertThat(context).hasSingleBean(SnsOutboxEventTransport.class);
                            assertThat(context).doesNotHaveBean(SqsClient.class);
                        });
    }

    @Test
    void doesNotCreateProviderClientsWhileRelayIsDisabled() {
        runner.withPropertyValues(
                        "blocbuy.events.enabled=true",
                        "blocbuy.events.relay.enabled=false",
                        "blocbuy.events.transport=sqs")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(SqsClient.class);
                            assertThat(context).doesNotHaveBean(SnsClient.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class JsonConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }
}
