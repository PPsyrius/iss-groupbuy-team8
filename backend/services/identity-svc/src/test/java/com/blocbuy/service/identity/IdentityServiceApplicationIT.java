package com.blocbuy.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.blocbuy.catalog.persistence.BizProductMapper;
import com.blocbuy.catalog.web.ProductController;
import com.blocbuy.common.config.MyBatisConfig;
import com.blocbuy.identity.persistence.SysUserMapper;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.service.UserAppServiceImpl;
import com.blocbuy.identity.web.AuthController;
import com.blocbuy.identity.web.internal.InternalUserController;
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
 * identity-svc assembles on its own (backend ADR 0008, #455 step A3): migrations and the grants
 * callback run against PostgreSQL, only this service's controllers and mappers are wired, and the
 * monolith-only global mapper scan is off. Redis is never contacted while the context starts.
 */
@SpringBootTest(
        classes = IdentityServiceApplication.class,
        properties = {"spring.data.redis.host=localhost", "spring.data.redis.port=1"})
@ActiveProfiles({"local", "identity"})
@Testcontainers
class IdentityServiceApplicationIT {

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
        assertThat(context.getBeanNamesForType(AuthController.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(SysUserMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ProductController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(BizProductMapper.class)).isEmpty();
        assertThat(context.getBeanNamesForType(NotificationController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(MyBatisConfig.class)).isEmpty();
        assertThat(context.getBean(TopologyProperties.class).topology())
                .isEqualTo(TopologyProperties.Mode.DISTRIBUTED);
    }

    /** Peers ask identity over /internal/users, answered by the local implementation (#468). */
    @Test
    void servesTheInternalUserEndpointsFromTheLocalImplementation() {
        assertThat(context.getBeanNamesForType(InternalUserController.class)).hasSize(1);
        assertThat(context.getBean(UserAppService.class)).isInstanceOf(UserAppServiceImpl.class);
    }

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {"--spring.profiles.active=identity"};

        try (MockedStatic<SpringApplication> springApplication =
                mockStatic(SpringApplication.class)) {
            IdentityServiceApplication.main(args);

            springApplication.verify(
                    () -> SpringApplication.run(IdentityServiceApplication.class, args));
        }
    }
}
