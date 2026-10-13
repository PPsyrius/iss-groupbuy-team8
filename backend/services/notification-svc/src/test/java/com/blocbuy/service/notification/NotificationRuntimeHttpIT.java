package com.blocbuy.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.model.NotificationType;
import com.blocbuy.notification.service.NotificationAppService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real notification assembly, restricted runtime role and shared Redis-backed login sessions. */
@SpringBootTest(
        classes = NotificationServiceApplication.class,
        properties = {"blocbuy.events.enabled=false", "blocbuy.scheduling.enabled=false"})
@ActiveProfiles({"local", "notification"})
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationRuntimeHttpIT {
    private static final String APP_USER = "blocbuy_notification_app";
    private static final String APP_PASSWORD = "notification-test-only";
    private static final String PATH = "/api/v1/notifications";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.10").withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        var owner =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        owner.execute("CREATE ROLE " + APP_USER + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
        owner.execute("CREATE SCHEMA domain");
        owner.execute("GRANT USAGE ON SCHEMA domain TO " + APP_USER);
        owner.execute(
                "ALTER DEFAULT PRIVILEGES IN SCHEMA domain "
                        + "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "
                        + APP_USER);
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", () -> APP_USER);
        properties.add("spring.datasource.password", () -> APP_PASSWORD);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
        properties.add("spring.flyway.placeholders.approle", () -> APP_USER);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private MockMvc mvc;
    @Autowired private NotificationAppService notifications;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;

    @Test
    void realSessionReadsOnlyOwnedRowsWhileRuntimeCannotAccessOtherDomains() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(APP_USER);
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT count(*) FROM sys_user", Long.class))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("permission denied");
        assertThatThrownBy(
                        () -> jdbc.queryForObject("SELECT count(*) FROM biz_product", Long.class))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("permission denied");
        UUID user = UuidV7.next();
        var own = notification(user);
        var foreign = notification(UuidV7.next());
        assertThat(notifications.recordBatch(List.of(own, foreign))).isEqualTo(2);
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        String token = StpUtil.getStpLogic().createLoginSession(user.toString());
        try {
            assertThat(redis.keys("*")).isNotEmpty();
            mvc.perform(
                            get(PATH)
                                    .param("userId", foreign.userId().toString())
                                    .header("blocbuy-token", token))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(own.id().toString()))
                    .andExpect(jsonPath("$.data.items[0].userId").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].eventId").doesNotExist());
            mvc.perform(get(PATH).param("size", "101").header("blocbuy-token", token))
                    .andExpect(status().isBadRequest());
        } finally {
            StpUtil.logoutByTokenValue(token);
        }
        mvc.perform(get(PATH).header("blocbuy-token", token)).andExpect(status().isUnauthorized());
    }

    private static Notification notification(UUID user) {
        return new Notification(
                UuidV7.next(),
                UuidV7.next(),
                user,
                NotificationType.TEAM_SUCCEEDED,
                UuidV7.next(),
                "Team succeeded",
                "Your team succeeded.",
                Instant.parse("2026-10-13T00:00:00Z"));
    }
}
