package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.common.gateway.OutboxEventTransport;
import com.blocbuy.common.persistence.UuidTypeHandler;
import com.blocbuy.common.service.OutboxRelayService;
import com.blocbuy.integration.outbox.OutboxInsert;
import com.blocbuy.integration.outbox.OutboxMapper;
import com.blocbuy.integration.outbox.OutboxRelayRepositoryImpl;
import com.blocbuy.integration.outbox.SqsOutboxEventTransport;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/** PostgreSQL-to-ElasticMQ outbox relay recovery check using the real mapper and SQS adapter. */
@Testcontainers(disabledWithoutDocker = true)
class OutboxRelayFlowIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Container
    static final GenericContainer<?> ELASTICMQ =
            new GenericContainer<>("softwaremill/elasticmq-native:1.7.1").withExposedPorts(9324);

    private static JdbcTemplate jdbc;
    private static SqlSessionFactory sessions;

    @BeforeAll
    static void initializeDatabase() throws Exception {
        var source =
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl() + "?currentSchema=domain",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA domain");
        Path migration =
                Path.of(
                        System.getProperty("basedir"),
                        "src",
                        "main",
                        "resources",
                        "db",
                        "migration",
                        "V26__transactional_outbox.sql");
        new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(source);
        var config =
                new org.apache.ibatis.session.Configuration(
                        new Environment("test", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        String resource = "mapper/integration/OutboxMapper.xml";
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    @Test
    void committedRowsSurviveBrokerOutageAndArePublishedWithAttributesAfterRecovery() {
        String endpoint =
                "http://%s:%d".formatted(ELASTICMQ.getHost(), ELASTICMQ.getMappedPort(9324));
        var credentials =
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        var quickFailure =
                ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofMillis(500))
                        .apiCallTimeout(Duration.ofSeconds(1))
                        .build();
        try (SqsClient realClient =
                        SqsClient.builder()
                                .region(Region.US_EAST_1)
                                .credentialsProvider(credentials)
                                .endpointOverride(URI.create(endpoint))
                                .build();
                SqsClient unavailableClient =
                        SqsClient.builder()
                                .region(Region.US_EAST_1)
                                .credentialsProvider(credentials)
                                .endpointOverride(URI.create("http://127.0.0.1:1"))
                                .overrideConfiguration(quickFailure)
                                .build()) {
            String queueUrl =
                    realClient
                            .createQueue(
                                    CreateQueueRequest.builder()
                                            .queueName("notification-events")
                                            .build())
                            .queueUrl();
            jdbc.execute("TRUNCATE sys_outbox");
            UUID aggregateId = UUID.randomUUID();
            List<UUID> ids = new ArrayList<>();
            try (SqlSession session = sessions.openSession(false)) {
                OutboxMapper mapper = session.getMapper(OutboxMapper.class);
                for (int index = 0; index < 3; index++) {
                    mapper.insert(
                            new OutboxInsert(
                                    "TeamExpired",
                                    1,
                                    aggregateId,
                                    "{\"expired\":true}",
                                    "trace-it-" + index));
                }
                session.commit();
                ids.addAll(jdbc.queryForList("SELECT id FROM sys_outbox ORDER BY id", UUID.class));
            }

            try (SqlSession relaySession = sessions.openSession(true)) {
                OutboxRelayRepositoryImpl repository =
                        new OutboxRelayRepositoryImpl(relaySession.getMapper(OutboxMapper.class));
                OutboxEventTransport unavailable =
                        new SqsOutboxEventTransport(
                                unavailableClient, queueUrl, JsonMapper.builder().build());
                OutboxRelayService failedRelay = new OutboxRelayService(repository, unavailable);
                assertThat(failedRelay.relayBatch()).isEqualTo(3);
                assertThat(
                                jdbc.queryForList(
                                        "SELECT attempts FROM sys_outbox ORDER BY id",
                                        Integer.class))
                        .containsExactly(1, 1, 1);
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM sys_outbox WHERE published_at IS NULL AND failed_at IS NULL",
                                        Integer.class))
                        .isEqualTo(3);

                OutboxEventTransport recovered =
                        new SqsOutboxEventTransport(
                                realClient, queueUrl, JsonMapper.builder().build());
                OutboxRelayService recoveredRelay = new OutboxRelayService(repository, recovered);
                // Fix eligibility independently of the one-second backoff and runner pauses.
                jdbc.update(
                        "UPDATE sys_outbox SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1 hour'");
                relaySession.clearCache();
                // Future-dated rows must not be sent before their retry deadline.
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM sys_outbox WHERE next_attempt_at > CURRENT_TIMESTAMP",
                                        Integer.class))
                        .isEqualTo(3);
                assertThat(recoveredRelay.relayBatch()).isZero();

                // let the backoff elapse without waiting for it
                jdbc.update(
                        "UPDATE sys_outbox SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
                // the empty poll above sits in this session's local cache; Spring opens a fresh
                // session per transaction, so production never reuses it
                relaySession.clearCache();
                assertThat(recoveredRelay.relayBatch()).isEqualTo(3);

                var messages =
                        realClient
                                .receiveMessage(
                                        ReceiveMessageRequest.builder()
                                                .queueUrl(queueUrl)
                                                .maxNumberOfMessages(10)
                                                .messageAttributeNames(List.of("All"))
                                                .waitTimeSeconds(1)
                                                .build())
                                .messages();
                assertThat(messages).hasSize(3);
                assertThat(messages)
                        .allSatisfy(
                                message -> {
                                    assertThat(message.messageAttributes())
                                            .containsKeys("eventType", "version", "traceId");
                                    assertThat(message.body())
                                            .contains("\"eventId\"")
                                            .contains("\"eventType\":\"TeamExpired\"");
                                });
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM sys_outbox WHERE published_at IS NOT NULL AND failed_at IS NULL",
                                        Integer.class))
                        .isEqualTo(3);
                assertThat(jdbc.queryForList("SELECT id FROM sys_outbox ORDER BY id", UUID.class))
                        .containsExactlyElementsOf(ids);
                assertThat(
                                jdbc.queryForList(
                                        "SELECT attempts FROM sys_outbox ORDER BY id",
                                        Integer.class))
                        .containsExactly(1, 1, 1);
            }
        }
    }
}
