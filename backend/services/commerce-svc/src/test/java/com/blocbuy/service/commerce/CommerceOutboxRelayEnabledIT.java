package com.blocbuy.service.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.bootstrap.scheduling.OutboxRelayJob;
import com.blocbuy.common.gateway.OutboxEventTransport;
import com.blocbuy.integration.outbox.SnsOutboxEventTransport;
import com.blocbuy.notification.messaging.SqsNotificationEventConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * The commerce assembly with the relay switched on as ECS will run it once #447 supplies the topic
 * (backend ADR 0007): SNS transport, the relay job under the scheduler lease, no SQS client. The
 * outbox is empty, so no provider call is made.
 */
@SpringBootTest(
        classes = CommerceServiceApplication.class,
        properties = {
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "blocbuy.identity.base-url=http://identity-svc.invalid:8589",
            "blocbuy.events.enabled=true",
            "blocbuy.events.relay.enabled=true",
            // commerce never scans notification packages, so this switch has no effect here
            "blocbuy.events.consumer.enabled=true",
            "blocbuy.events.region=ap-southeast-1",
            "blocbuy.events.sns.topic-arn=arn:aws:sns:ap-southeast-1:000000000000:events",
            "blocbuy.scheduling.enabled=true",
            "blocbuy.events.retention.initial-delay=PT24H",
            "blocbuy.scheduling.team-expiry.initial-delay=PT24H",
            "blocbuy.scheduling.refund-worker.initial-delay=PT24H",
            "blocbuy.scheduling.purchase-intent-expiry.initial-delay=PT24H"
        })
@ActiveProfiles({"local", "commerce"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommerceOutboxRelayEnabledIT {

    @Container
    @SuppressWarnings("resource") // Testcontainers owns this disposable database.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ApplicationContext context;

    @Test
    void publishesThroughSnsFromTheCommerceProfile() {
        assertThat(context.getBeanNamesForType(OutboxRelayJob.class)).hasSize(1);
        assertThat(context.getBeansOfType(OutboxEventTransport.class).values())
                .singleElement()
                .isInstanceOf(SnsOutboxEventTransport.class);
        assertThat(context.getBean(SnsClient.class).serviceClientConfiguration().region())
                .isEqualTo(Region.AP_SOUTHEAST_1);
        assertThat(context.getBeanNamesForType(SqsClient.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SqsNotificationEventConsumer.class)).isEmpty();
    }
}
