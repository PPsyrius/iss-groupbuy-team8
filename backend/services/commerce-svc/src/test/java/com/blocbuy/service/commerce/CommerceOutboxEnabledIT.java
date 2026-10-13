package com.blocbuy.service.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.bootstrap.scheduling.OutboxRetentionJob;
import com.blocbuy.common.repository.OutboxRetentionRepository;
import com.blocbuy.groupbuy.event.TeamSucceeded;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.integration.outbox.IntegrationOutboxWriter;
import com.blocbuy.integration.outbox.OutboxMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Actual commerce assembly with deployment event/scheduling switches, not monolith scanning. */
@SpringBootTest(
        classes = CommerceServiceApplication.class,
        properties = {
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "blocbuy.events.enabled=true",
            "blocbuy.scheduling.enabled=true",
            "blocbuy.events.retention.initial-delay=PT24H",
            "blocbuy.scheduling.team-expiry.initial-delay=PT24H",
            "blocbuy.scheduling.refund-worker.initial-delay=PT24H",
            "blocbuy.scheduling.purchase-intent-expiry.initial-delay=PT24H"
        })
@ActiveProfiles({"local", "commerce"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommerceOutboxEnabledIT {
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

    // Retain only the agreed #468 dependency mock; the writer, mapper and retention are real.
    @MockitoBean private UserAppService users;
    @Autowired private ApplicationContext context;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void wiresAndPersistsOutboxFactsInTheActualCommerceAssembly() {
        assertThat(context.getBeansOfType(IntegrationOutboxWriter.class)).hasSize(1);
        assertThat(context.getBeansOfType(OutboxMapper.class)).hasSize(1);
        assertThat(context.getBeansOfType(OutboxRetentionRepository.class)).hasSize(1);
        assertThat(context.getBeansOfType(OutboxRetentionJob.class)).hasSize(1);
        UUID deal = UUID.randomUUID();
        var event = new TeamSucceeded(deal, UUID.randomUUID(), 2, Instant.now());
        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> events.publishEvent(event));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sys_outbox WHERE aggregate_id = ? AND event_type = 'TeamSucceeded'",
                                Long.class,
                                deal))
                .isEqualTo(1);
    }

    @Test
    void retentionRunIsCappedEvenWhenMoreExpiredRowsExist() {
        jdbc.execute(
                """
                INSERT INTO sys_outbox (event_type, event_version, aggregate_id, payload, trace_id, published_at)
                SELECT 'TeamExpired', 1, uuidv7(), '{}', 'retention-test', clock_timestamp() - interval '8 days'
                FROM generate_series(1, 501)
                """);
        context.getBean(OutboxRetentionJob.class).run();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sys_outbox WHERE trace_id = 'retention-test'",
                                Long.class))
                .isEqualTo(1);
        context.getBean(OutboxRetentionJob.class).run();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sys_outbox WHERE trace_id = 'retention-test'",
                                Long.class))
                .isZero();
    }
}
