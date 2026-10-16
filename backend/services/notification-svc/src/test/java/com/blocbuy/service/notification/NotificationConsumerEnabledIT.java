package com.blocbuy.service.notification;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * notification-svc with {@code BLOCBUY_EVENTS_CONSUMER_ENABLED=true} (#446): the SQS consumer is
 * assembled and running. The queue endpoint is unreachable on purpose; failed receives back off and
 * never stop the context. Message handling itself is covered by {@code
 * NotificationConsumerElasticMqIT} in the monolith, which wires the same beans.
 */
@SpringBootTest(
        classes = NotificationServiceApplication.class,
        properties = {
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "blocbuy.events.consumer.enabled=true",
            "blocbuy.events.consumer.wait-time=1s",
            "blocbuy.events.consumer.error-backoff=1s",
            "blocbuy.events.consumer.shutdown-timeout=1s",
            "blocbuy.events.region=us-east-1",
            "blocbuy.events.sqs.endpoint=http://127.0.0.1:1",
            "blocbuy.events.sqs.queue-url=http://127.0.0.1:1/000000000000/notification-events"
        })
@ActiveProfiles({"local", "notification"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationConsumerEnabledIT {

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
    void consumerIsAssembledAndRunningWhenEnabled() {
        assertThat(context.getBeansOfType(SqsNotificationEventConsumer.class).values())
                .singleElement()
                .satisfies(consumer -> assertThat(consumer.isRunning()).isTrue());
    }
}
