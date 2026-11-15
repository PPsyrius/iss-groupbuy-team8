package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;

import com.blocbuy.common.repository.OutboxRelayRepository;
import com.blocbuy.common.service.OutboxRelayService;
import com.blocbuy.groupbuy.service.GroupDealExpiryService;
import com.blocbuy.integration.outbox.IntegrationOutboxWriter;
import com.blocbuy.integration.outbox.SqsOutboxEventTransport;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

/** Real business rollback must leave nothing for the PostgreSQL-to-ElasticMQ relay (#448). */
@SpringBootTest(
        properties = {
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "blocbuy.events.enabled=true",
            "blocbuy.events.relay.enabled=false",
            "blocbuy.events.consumer.enabled=false",
            "blocbuy.scheduling.enabled=false"
        })
@Testcontainers
@DirtiesContext
class OutboxRollbackElasticMqIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Container
    static final GenericContainer<?> ELASTICMQ =
            new GenericContainer<>("softwaremill/elasticmq-native:1.7.1").withExposedPorts(9324);

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

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private GroupDealExpiryService expiry;
    @Autowired private OutboxRelayRepository outbox;
    @MockitoSpyBean private IntegrationOutboxWriter writer;

    @Test
    void rolledBackTeamExpiryPublishesNothingToElasticMqButCommittedRetryDoes() {
        UUID deal = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO biz_group_deal
                    (id,deal_no,product_id,leader_user_id,deal_price,currency,target_size,
                     current_size,status,start_time,expire_time)
                VALUES (?, ?, ?, ?, 10, 'sgd', 2, 0, 'open',
                        CURRENT_TIMESTAMP - INTERVAL '2 hours',
                        CURRENT_TIMESTAMP - INTERVAL '1 hour')
                """,
                deal,
                deal.toString(),
                UUID.randomUUID(),
                UUID.randomUUID());
        var transaction = new TransactionTemplate(transactions);
        transaction.setTimeout(10);
        String endpoint =
                "http://%s:%d".formatted(ELASTICMQ.getHost(), ELASTICMQ.getMappedPort(9324));
        try (SqsClient sqs =
                SqsClient.builder()
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test", "test")))
                        .endpointOverride(URI.create(endpoint))
                        .overrideConfiguration(
                                ClientOverrideConfiguration.builder()
                                        .apiCallAttemptTimeout(Duration.ofSeconds(2))
                                        .apiCallTimeout(Duration.ofSeconds(3))
                                        .build())
                        .build()) {
            String queueUrl =
                    sqs.createQueue(request -> request.queueName("rollback-" + deal)).queueUrl();
            var transport =
                    new SqsOutboxEventTransport(sqs, queueUrl, JsonMapper.builder().build());
            var relay = new OutboxRelayService(outbox, transport);

            assertThatThrownBy(() ->
                            transaction.executeWithoutResult(status -> {
                                assertThat(expiry.expireOne(deal)).isTrue();
                                assertThat(teamStatus(deal)).isEqualTo("expired");
                                throw new IllegalStateException("force business rollback");
                            }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("force business rollback");

            assertThat(teamStatus(deal)).isEqualTo("open");
            assertThat(outboxRows(deal)).isZero();
            assertThat(outbox.findDue(100)).isEmpty();
            assertThat(relay.relayBatch()).isZero();
            assertThat(
                            sqs.receiveMessage(request ->
                                            request.queueUrl(queueUrl).waitTimeSeconds(1))
                                    .messages())
                    .isEmpty();

            // Same business operation, listener, mapper, relay and queue: prove the path is live.
            assertThat(expiry.expireOne(deal)).isTrue();
            assertThat(teamStatus(deal)).isEqualTo("expired");
            assertThat(outbox.findDue(100))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.aggregateId()).isEqualTo(deal);
                        assertThat(event.eventType()).isEqualTo("TeamExpired");
                    });
            assertThat(relay.relayBatch()).isEqualTo(1);
            assertThat(
                            sqs.receiveMessage(request ->
                                            request.queueUrl(queueUrl).waitTimeSeconds(1))
                                    .messages())
                    .singleElement()
                    .satisfies(message ->
                            assertThat(message.body())
                                    .contains("\"eventType\":\"TeamExpired\"")
                                    .contains(deal.toString()));
            assertThat(outbox.findDue(100)).isEmpty();
        }
    }

    @Test
    void failureAfterOutboxInsertRollsBackTeamAndEventBeforeCommittedRetryDelivers() {
        UUID deal = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO biz_group_deal
                    (id,deal_no,product_id,leader_user_id,deal_price,currency,target_size,
                     current_size,status,start_time,expire_time)
                VALUES (?, ?, ?, ?, 10, 'sgd', 2, 0, 'open',
                        CURRENT_TIMESTAMP - INTERVAL '2 hours',
                        CURRENT_TIMESTAMP - INTERVAL '1 hour')
                """,
                deal,
                deal.toString(),
                UUID.randomUUID(),
                UUID.randomUUID());
        var transaction = new TransactionTemplate(transactions);
        transaction.setTimeout(10);
        var insertObserved = new AtomicBoolean();
        var failure = new IllegalStateException("force rollback after outbox insert");
        // The writer already has LOWEST_PRECEDENCE. Hook its return instead of tying listener
        // order.
        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            assertThat(teamStatus(deal)).isEqualTo("expired");
                            assertThat(outboxRows(deal)).isEqualTo(1);
                            insertObserved.set(true);
                            throw failure;
                        })
                .when(writer)
                .onTeamExpired(argThat(event -> event.dealId().equals(deal)));
        String endpoint =
                "http://%s:%d".formatted(ELASTICMQ.getHost(), ELASTICMQ.getMappedPort(9324));
        try (SqsClient sqs =
                SqsClient.builder()
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test", "test")))
                        .endpointOverride(URI.create(endpoint))
                        .overrideConfiguration(
                                ClientOverrideConfiguration.builder()
                                        .apiCallAttemptTimeout(Duration.ofSeconds(2))
                                        .apiCallTimeout(Duration.ofSeconds(3))
                                        .build())
                        .build()) {
            String queueUrl =
                    sqs.createQueue(request -> request.queueName("post-insert-" + deal)).queueUrl();
            var relay =
                    new OutboxRelayService(
                            outbox,
                            new SqsOutboxEventTransport(
                                    sqs, queueUrl, JsonMapper.builder().build()));

            // Return normally: the failure must come from the real BEFORE_COMMIT writer.
            assertThatThrownBy(() ->
                            transaction.executeWithoutResult(status -> {
                                assertThat(expiry.expireOne(deal)).isTrue();
                                assertThat(teamStatus(deal)).isEqualTo("expired");
                            }))
                    .isSameAs(failure);
            assertThat(insertObserved).isTrue();
            verify(writer).onTeamExpired(argThat(event -> event.dealId().equals(deal)));
            assertThat(teamStatus(deal)).isEqualTo("open");
            assertThat(outboxRows(deal)).isZero();
            assertThat(outbox.findDue(100)).isEmpty();
            assertThat(relay.relayBatch()).isZero();
            assertThat(
                            sqs.receiveMessage(request ->
                                            request.queueUrl(queueUrl).waitTimeSeconds(1))
                                    .messages())
                    .isEmpty();

            doCallRealMethod()
                    .when(writer)
                    .onTeamExpired(argThat(event -> event.dealId().equals(deal)));
            assertThat(expiry.expireOne(deal)).isTrue();
            assertThat(teamStatus(deal)).isEqualTo("expired");
            assertThat(outboxRows(deal)).isEqualTo(1);
            assertThat(outbox.findDue(100))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.aggregateId()).isEqualTo(deal);
                        assertThat(event.eventType()).isEqualTo("TeamExpired");
                    });
            assertThat(relay.relayBatch()).isEqualTo(1);
            assertThat(
                            sqs.receiveMessage(request ->
                                            request.queueUrl(queueUrl).waitTimeSeconds(1))
                                    .messages())
                    .singleElement()
                    .satisfies(message ->
                            assertThat(message.body())
                                    .contains("\"eventType\":\"TeamExpired\"")
                                    .contains(deal.toString()));
            assertThat(outbox.findDue(100)).isEmpty();
        }
    }

    private long outboxRows(UUID deal) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM sys_outbox WHERE aggregate_id=? AND event_type='TeamExpired'",
                Long.class,
                deal);
    }

    private String teamStatus(UUID deal) {
        return jdbc.queryForObject(
                "SELECT status FROM biz_group_deal WHERE id=?", String.class, deal);
    }
}
