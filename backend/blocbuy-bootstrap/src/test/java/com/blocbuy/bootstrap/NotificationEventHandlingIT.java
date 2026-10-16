package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.blocbuy.common.util.UuidV7;
import com.blocbuy.notification.event.NotificationEventHandler;
import com.blocbuy.notification.event.NotificationEventHandler.ConsumeOutcome;
import com.blocbuy.notification.model.NotificationEvent;
import com.blocbuy.notification.service.NotificationAppService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The actual transactional handler against PostgreSQL with the full Flyway chain (#446): the
 * consumed-message marker and the notifications commit together, exactly once per eventId.
 */
@SpringBootTest(properties = {"spring.data.redis.host=localhost", "spring.data.redis.port=1"})
@Testcontainers
@Import(OrderControllerTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationEventHandlingIT {
    private static final String CONSUMER = "notification";

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

    @Autowired private NotificationEventHandler handler;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private NotificationAppService notifications;

    @Test
    void sameEventAppliedThreeTimesCreatesOneNotificationPerRecipientAndOneMarker() {
        UUID a = UuidV7.next();
        UUID b = UuidV7.next();
        var event =
                new NotificationEvent.TeamSucceeded(UuidV7.next(), UuidV7.next(), 2, List.of(a, b));

        assertThat(handler.handle(CONSUMER, event)).isEqualTo(ConsumeOutcome.APPLIED);
        assertThat(handler.handle(CONSUMER, event)).isEqualTo(ConsumeOutcome.DUPLICATE);
        assertThat(handler.handle(CONSUMER, event)).isEqualTo(ConsumeOutcome.DUPLICATE);

        assertThat(
                        jdbc.queryForList(
                                "SELECT user_id FROM biz_notification WHERE event_id = ?",
                                UUID.class,
                                event.eventId()))
                .containsExactlyInAnyOrder(a, b);
        assertThat(markers(event.eventId())).isEqualTo(1);
    }

    @Test
    void failureInsideTheTransactionLeavesNeitherMarkerNorNotificationsAndRetrySucceeds() {
        var event =
                new NotificationEvent.TeamExpired(
                        UuidV7.next(), UuidV7.next(), List.of(UuidV7.next(), UuidV7.next()));
        AtomicInteger writtenBeforeFailure = new AtomicInteger(-1);
        // Fail after the real batch insert ran inside the handler's transaction.
        doAnswer(
                        call -> {
                            writtenBeforeFailure.set((Integer) call.callRealMethod());
                            throw new IllegalStateException("injected failure after insert");
                        })
                .when(notifications)
                .recordBatch(anyList());

        assertThatThrownBy(() -> handler.handle(CONSUMER, event))
                .isInstanceOf(IllegalStateException.class);
        assertThat(writtenBeforeFailure.get()).isEqualTo(2);
        assertThat(markers(event.eventId())).isZero();
        assertThat(notificationsFor(event.eventId())).isZero();

        reset(notifications);
        assertThat(handler.handle(CONSUMER, event)).isEqualTo(ConsumeOutcome.APPLIED);
        assertThat(markers(event.eventId())).isEqualTo(1);
        assertThat(notificationsFor(event.eventId())).isEqualTo(2);
    }

    @Test
    void emptyRecipientEventIsMarkedConsumedWithoutNotifications() {
        var event = new NotificationEvent.TeamExpired(UuidV7.next(), UuidV7.next(), List.of());

        assertThat(handler.handle(CONSUMER, event)).isEqualTo(ConsumeOutcome.APPLIED);
        assertThat(handler.handle(CONSUMER, event)).isEqualTo(ConsumeOutcome.DUPLICATE);
        assertThat(markers(event.eventId())).isEqualTo(1);
        assertThat(notificationsFor(event.eventId())).isZero();
    }

    @Test
    void concurrentDeliveriesOfOneEventApplyItOnce() throws Exception {
        var event =
                new NotificationEvent.TeamExpired(
                        UuidV7.next(), UuidV7.next(), List.of(UuidV7.next(), UuidV7.next()));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<ConsumeOutcome> deliver =
                    () -> {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test start timed out");
                        }
                        return handler.handle(CONSUMER, event);
                    };
            var first = executor.submit(deliver);
            var second = executor.submit(deliver);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(ConsumeOutcome.APPLIED, ConsumeOutcome.DUPLICATE);
        }
        assertThat(markers(event.eventId())).isEqualTo(1);
        assertThat(notificationsFor(event.eventId())).isEqualTo(2);
    }

    private long markers(UUID eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM sys_consumed_message WHERE event_id = ? AND consumer = ?",
                Long.class,
                eventId,
                CONSUMER);
    }

    private long notificationsFor(UUID eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM biz_notification WHERE event_id = ?", Long.class, eventId);
    }
}
