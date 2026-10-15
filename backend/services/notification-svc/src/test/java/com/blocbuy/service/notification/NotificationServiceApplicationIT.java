package com.blocbuy.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.blocbuy.bootstrap.scheduling.OutboxRelayJob;
import com.blocbuy.catalog.persistence.BizProductMapper;
import com.blocbuy.catalog.web.ProductController;
import com.blocbuy.common.config.MyBatisConfig;
import com.blocbuy.common.config.SessionStpInterface;
import com.blocbuy.common.gateway.OutboxEventTransport;
import com.blocbuy.identity.persistence.SysUserMapper;
import com.blocbuy.identity.web.AuthController;
import com.blocbuy.integration.outbox.IntegrationOutboxWriter;
import com.blocbuy.notification.event.NotificationEventHandler;
import com.blocbuy.notification.messaging.SqsNotificationEventConsumer;
import com.blocbuy.notification.web.NotificationController;
import com.blocbuy.platform.TopologyProperties;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * notification-svc assembles on its own (backend ADR 0008, #455 step A3): migrations and the grants
 * callback run against PostgreSQL, only this service's controllers and mappers are wired, and the
 * monolith-only global mapper scan is off. Redis is never contacted while the context starts.
 */
@SpringBootTest(
        classes = NotificationServiceApplication.class,
        properties = {
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            // switched on to prove the outbox relay stays a commerce-only assembly (ADR 0007)
            "blocbuy.events.enabled=true",
            "blocbuy.events.relay.enabled=true"
        })
@ActiveProfiles({"local", "notification"})
@Testcontainers
class NotificationServiceApplicationIT {

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
    void wiresOnlyThisServicesContexts() {
        assertThat(context.getBeanNamesForType(NotificationController.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(NotificationEventHandler.class)).hasSize(1);
        // the SQS consumer is opt-in (blocbuy.events.consumer.enabled), off by default
        assertThat(context.getBeanNamesForType(SqsNotificationEventConsumer.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AuthController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ProductController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SysUserMapper.class)).isEmpty();
        assertThat(context.getBeanNamesForType(BizProductMapper.class)).isEmpty();
        assertThat(context.getBeanNamesForType(MyBatisConfig.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OutboxRelayJob.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OutboxEventTransport.class)).isEmpty();
        assertThat(context.getBeanNamesForType(IntegrationOutboxWriter.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SessionStpInterface.class)).hasSize(1);
        assertThat(context.getBean(TopologyProperties.class).topology())
                .isEqualTo(TopologyProperties.Mode.DISTRIBUTED);
    }

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {"--spring.profiles.active=notification"};

        try (MockedStatic<SpringApplication> springApplication =
                mockStatic(SpringApplication.class)) {
            NotificationServiceApplication.main(args);

            springApplication.verify(
                    () -> SpringApplication.run(NotificationServiceApplication.class, args));
        }
    }
}
