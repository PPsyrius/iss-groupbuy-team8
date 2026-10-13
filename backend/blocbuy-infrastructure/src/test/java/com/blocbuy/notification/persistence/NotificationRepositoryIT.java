package com.blocbuy.notification.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.persistence.UuidTypeHandler;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.model.NotificationType;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class NotificationRepositoryIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static SqlSessionFactory sessions;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void configure() throws Exception {
        String url = POSTGRES.getJdbcUrl();
        var dataSource =
                new DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=domain",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA domain");
        var migration =
                Path.of(
                        System.getProperty("basedir"),
                        "..",
                        "blocbuy-bootstrap",
                        "src",
                        "main",
                        "resources",
                        "db",
                        "migration",
                        "V27__notification.sql");
        new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(dataSource);
        var config =
                new Configuration(
                        new Environment("test", new JdbcTransactionFactory(), dataSource));
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        String resource = "mapper/notification/BizNotificationMapper.xml";
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    @Test
    void roundTripsAndPagesOnlyTheRecipientsActiveRowsWithStableOrdering() {
        UUID user = UuidV7.next();
        var first =
                notification(
                        user,
                        UuidV7.next(),
                        UUID.fromString("01990000-0000-7000-8000-000000000001"));
        var second =
                notification(
                        user,
                        UuidV7.next(),
                        UUID.fromString("01990000-0000-7000-8000-000000000002"));
        var foreign = notification(UuidV7.next(), first.eventId());
        try (var session = sessions.openSession()) {
            var repository =
                    new NotificationRepositoryImpl(session.getMapper(BizNotificationMapper.class));
            assertThat(repository.insertIfAbsent(List.of(first, second, foreign))).isEqualTo(3);
            assertThat(repository.findById(first.id())).contains(first);
            assertThat(repository.findById(UuidV7.next())).isEmpty();
            var page = repository.pageByUserId(new PageQuery(1, 1), user);
            assertThat(page.items()).containsExactly(second);
            assertThat(page.total()).isEqualTo(2);
            assertThat(repository.pageByUserId(new PageQuery(2, 1), user).items())
                    .containsExactly(first);
            assertThat(repository.pageByUserId(new PageQuery(3, 1), user).items()).isEmpty();
            session.commit();
        }
        jdbc.update("UPDATE biz_notification SET deleted = true WHERE id = ?", second.id());
        try (var session = sessions.openSession()) {
            var repository =
                    new NotificationRepositoryImpl(session.getMapper(BizNotificationMapper.class));
            assertThat(repository.findById(second.id())).isEmpty();
            assertThat(repository.pageByUserId(new PageQuery(1, 20), user).items())
                    .containsExactly(first);
            // Deleted effects are not resurrected by redelivery.
            assertThat(repository.insertIfAbsent(List.of(notification(user, second.eventId()))))
                    .isZero();
        }
    }

    @Test
    void repeatedEventRecipientIsANoopAndRollbackLeavesNoEffect() {
        var item = notification(UuidV7.next(), UuidV7.next());
        try (var session = sessions.openSession()) {
            var repository =
                    new NotificationRepositoryImpl(session.getMapper(BizNotificationMapper.class));
            assertThat(
                            repository.insertIfAbsent(
                                    List.of(item, notification(item.userId(), item.eventId()))))
                    .isEqualTo(1);
            assertThat(
                            repository.insertIfAbsent(
                                    List.of(notification(item.userId(), item.eventId()))))
                    .isZero();
            assertThat(repository.findById(item.id())).contains(item);
            session.rollback();
        }
        try (var session = sessions.openSession()) {
            var repository =
                    new NotificationRepositoryImpl(session.getMapper(BizNotificationMapper.class));
            assertThat(repository.findById(item.id())).isEmpty();
            assertThat(repository.pageByUserId(new PageQuery(1, 20), item.userId()).total())
                    .isZero();
        }
    }

    @Test
    void concurrentRedeliveryCommitsOnlyOneRecipientEffect() throws Exception {
        UUID user = UuidV7.next();
        UUID event = UuidV7.next();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> insert =
                    () -> {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test start timed out");
                        }
                        try (var session = sessions.openSession()) {
                            var repository =
                                    new NotificationRepositoryImpl(
                                            session.getMapper(BizNotificationMapper.class));
                            int count =
                                    repository.insertIfAbsent(List.of(notification(user, event)));
                            session.commit();
                            return count;
                        }
                    };
            var first = executor.submit(insert);
            var second = executor.submit(insert);
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS))
                    .isEqualTo(1);
        }
        try (var session = sessions.openSession()) {
            var repository =
                    new NotificationRepositoryImpl(session.getMapper(BizNotificationMapper.class));
            assertThat(repository.pageByUserId(new PageQuery(1, 20), user).total()).isEqualTo(1);
        }
    }

    private static Notification notification(UUID user, UUID event) {
        return notification(user, event, UuidV7.next());
    }

    private static Notification notification(UUID user, UUID event, UUID id) {
        return new Notification(
                id,
                event,
                user,
                NotificationType.TEAM_SUCCEEDED,
                UuidV7.next(),
                "Team succeeded",
                "Your team succeeded.",
                Instant.parse("2026-10-13T00:00:00Z"));
    }
}
