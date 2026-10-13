package com.blocbuy.service.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.blocbuy.bootstrap.scheduling.OutboxRetentionJob;
import com.blocbuy.catalog.persistence.BizProductMapper;
import com.blocbuy.catalog.web.ProductController;
import com.blocbuy.common.config.MyBatisConfig;
import com.blocbuy.common.config.SessionStpInterface;
import com.blocbuy.identity.persistence.SysUserMapper;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.web.AuthController;
import com.blocbuy.identity.web.internal.InternalUserController;
import com.blocbuy.integration.outbox.IntegrationOutboxWriter;
import com.blocbuy.integration.outbox.OutboxMapper;
import com.blocbuy.notification.web.NotificationController;
import com.blocbuy.order.persistence.BizOrderMapper;
import com.blocbuy.platform.TopologyProperties;
import com.blocbuy.service.commerce.identity.RemoteUserAppService;
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
 * commerce-svc assembles on its own (backend ADR 0008, #455 step A3): migrations and the grants
 * callback run against PostgreSQL, only this service's controllers and mappers are wired, and the
 * monolith-only global mapper scan is off. Redis is never contacted while the context starts.
 */
@SpringBootTest(
        classes = CommerceServiceApplication.class,
        properties = {
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            // never contacted while the context starts; the remote client only stores it
            "blocbuy.identity.base-url=http://identity-svc.invalid:8589",
            "blocbuy.events.enabled=false",
            "blocbuy.scheduling.enabled=false"
        })
@ActiveProfiles({"local", "commerce"})
@Testcontainers
class CommerceServiceApplicationIT {

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
        assertThat(context.getBeanNamesForType(ProductController.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(BizProductMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(BizOrderMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(AuthController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SysUserMapper.class)).isEmpty();
        assertThat(context.getBeanNamesForType(NotificationController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(MyBatisConfig.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SessionStpInterface.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OutboxMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(IntegrationOutboxWriter.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OutboxRetentionJob.class)).isEmpty();
        assertThat(context.getBean(TopologyProperties.class).topology())
                .isEqualTo(TopologyProperties.Mode.DISTRIBUTED);
    }

    /**
     * Identity is answered by identity-svc over HTTP; this assembly serves no /internal/** (#468).
     */
    @Test
    void asksIdentityRemotely() {
        assertThat(context.getBeansOfType(UserAppService.class).values())
                .singleElement()
                .isInstanceOf(RemoteUserAppService.class);
        assertThat(context.getBeanNamesForType(InternalUserController.class)).isEmpty();
    }

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {"--spring.profiles.active=commerce"};

        try (MockedStatic<SpringApplication> springApplication =
                mockStatic(SpringApplication.class)) {
            CommerceServiceApplication.main(args);

            springApplication.verify(
                    () -> SpringApplication.run(CommerceServiceApplication.class, args));
        }
    }
}
