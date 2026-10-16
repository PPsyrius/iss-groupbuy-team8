package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.model.NotificationType;
import com.blocbuy.notification.service.NotificationAppService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Actual application proxy, full Flyway chain, MyBatis and authenticated HTTP recipient scoping.
 */
@SpringBootTest(properties = {"spring.data.redis.host=localhost", "spring.data.redis.port=1"})
@AutoConfigureMockMvc
@Testcontainers
@Import({
    OrderControllerTest.SessionStore.class,
    NotificationPersistenceIT.SnapshotConfiguration.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationPersistenceIT {
    @Container
    @SuppressWarnings("resource")
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

    @Autowired private NotificationAppService service;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SnapshotBarrier snapshotBarrier;

    @Test
    void actualReadNeverLeaksAnotherRecipientsRows() throws Exception {
        UUID user = UuidV7.next();
        var own = notification(user);
        var foreign = notification(UuidV7.next());
        assertThat(service.recordBatch(List.of(own, foreign))).isEqualTo(2);
        assertThat(service.recordBatch(List.of(own, foreign))).isZero();
        String token = StpUtil.getStpLogic().createLoginSession(user.toString());
        try {
            mvc.perform(
                            get("/api/v1/notifications")
                                    .param("userId", foreign.userId().toString())
                                    .header("blocbuy-token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(own.id().toString()));
        } finally {
            StpUtil.logoutByTokenValue(token);
        }
    }

    @Test
    void batchJoinsTheCallerTransactionAndCanBeRetriedAfterRollback() {
        var item = notification(UuidV7.next());
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            assertThat(service.recordBatch(List.of(item))).isEqualTo(1);
                            status.setRollbackOnly();
                        });
        assertThat(service.pageMine(new PageQuery(1, 20), item.userId()).total()).isZero();
        assertThat(service.recordBatch(List.of(item))).isEqualTo(1);
        assertThat(service.pageMine(new PageQuery(1, 20), item.userId()).items())
                .containsExactly(item);
    }

    @Test
    void databaseFailureInBatchLeavesNoPartialEffectsAndCanBeRetried() {
        UUID user = UuidV7.next();
        var existing = notification(user);
        service.recordBatch(List.of(existing));
        var first = notification(user);
        var second = notification(user);
        var colliding =
                new Notification(
                        existing.id(),
                        second.eventId(),
                        user,
                        second.type(),
                        second.referenceId(),
                        second.title(),
                        second.content(),
                        second.createTime());
        var failingBatch = List.of(first, colliding);
        assertThatThrownBy(() -> service.recordBatch(failingBatch))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(service.pageMine(new PageQuery(1, 20), user).items()).containsExactly(existing);
        assertThat(service.recordBatch(List.of(first, second))).isEqualTo(2);
        assertThat(service.pageMine(new PageQuery(1, 20), user).total()).isEqualTo(3);
    }

    @Test
    void pageAndCountKeepOneSnapshotWhenAnInsertCommitsBetweenQueries() throws Exception {
        UUID user = UuidV7.next();
        var first = notification(user);
        var arriving = notification(user);
        service.recordBatch(List.of(first));
        try (var executor = Executors.newSingleThreadExecutor()) {
            // Only intercept the boundary between real SQL calls; both reads and the writer are
            // real.
            snapshotBarrier.afterPage.set(
                    () -> {
                        assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class))
                                .isEqualTo("repeatable read");
                        assertThat(
                                        executor.submit(
                                                        () ->
                                                                service.recordBatch(
                                                                        List.of(arriving)))
                                                .get(10, TimeUnit.SECONDS))
                                .isEqualTo(1);
                        return null;
                    });
            var page = service.pageMine(new PageQuery(1, 20), user);
            assertThat(page.items()).containsExactly(first);
            assertThat(page.total()).isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM biz_notification WHERE user_id = ?",
                                    Long.class,
                                    user))
                    .isEqualTo(2);
        } finally {
            snapshotBarrier.afterPage.set(null);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SnapshotConfiguration {
        @Bean
        SnapshotBarrier snapshotBarrier() {
            return new SnapshotBarrier();
        }
    }

    /** Runs a one-shot test barrier after the actual page SELECT, before the count SELECT. */
    @Intercepts(
            @Signature(
                    type = Executor.class,
                    method = "query",
                    args = {
                        MappedStatement.class,
                        Object.class,
                        RowBounds.class,
                        ResultHandler.class
                    }))
    static class SnapshotBarrier implements Interceptor {
        final AtomicReference<Callable<Void>> afterPage = new AtomicReference<>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            Object result = invocation.proceed();
            var statement = (MappedStatement) invocation.getArgs()[0];
            if (statement
                    .getId()
                    .equals(
                            "com.blocbuy.notification.persistence.BizNotificationMapper.selectPageByUserId")) {
                var barrier = afterPage.getAndSet(null);
                if (barrier != null) {
                    barrier.call();
                }
            }
            return result;
        }
    }

    private static Notification notification(UUID user) {
        return new Notification(
                UuidV7.next(),
                UuidV7.next(),
                user,
                NotificationType.TEAM_EXPIRED,
                UuidV7.next(),
                "Team expired",
                "Your team expired.",
                Instant.parse("2026-10-13T00:00:00Z"));
    }
}
