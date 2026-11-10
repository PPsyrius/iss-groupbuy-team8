package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * Real scheduled expiry/outbox/relay/consumer rehearsal using the local SQS transport to ElasticMQ;
 * AWS SNS fan-out and autoscaling are not simulated.
 */
@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "blocbuy.scheduling.enabled=true",
            "BLOCBUY_SCHEDULING_TEAM_EXPIRY_INTERVAL=PT1S",
            "BLOCBUY_SCHEDULING_TEAM_EXPIRY_BATCH_SIZE=500",
            "blocbuy.events.enabled=true",
            "blocbuy.events.relay.enabled=true",
            "blocbuy.events.transport=sqs",
            "blocbuy.events.region=us-east-1",
            "blocbuy.events.consumer.enabled=true",
            "blocbuy.events.consumer.concurrency=4",
            "blocbuy.events.consumer.wait-time=1s",
            "blocbuy.events.consumer.visibility-timeout=30s",
            "blocbuy.events.consumer.shutdown-timeout=5s",
            "blocbuy.events.consumer.error-backoff=200ms"
        })
@Testcontainers
@Import(OrderControllerTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class V4ExpiryPipelineIT {
    private static final String RECIPIENT = "0199aaaa-0000-7000-8000-000000000001";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Container
    static final GenericContainer<?> ELASTICMQ =
            new GenericContainer<>("softwaremill/elasticmq-native:1.7.1")
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("../elasticmq.conf"), "/opt/elasticmq.conf")
                    .withExposedPorts(9324);

    private static SqsClient sqs;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("blocbuy.events.sqs.endpoint", V4ExpiryPipelineIT::endpoint);
        properties.add("blocbuy.events.sqs.queue-url", V4ExpiryPipelineIT::queue);
    }

    @BeforeAll
    static void credentials() {
        System.setProperty("aws.accessKeyId", "local");
        System.setProperty("aws.secretAccessKey", "local");
        sqs =
                SqsClient.builder()
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("local", "local")))
                        .endpointOverride(URI.create(endpoint()))
                        .build();
    }

    @AfterAll
    static void close() {
        if (sqs != null) sqs.close();
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
    }

    @Test
    void twoThousandSeededTeamsExpireAndProduceExactlyOneEffectEach() throws Exception {
        jdbc.update(
                "INSERT INTO sys_user(id,nickname,register_source,status) VALUES (?::uuid,'V4 fixture','local','active')",
                RECIPIENT);
        var resource = new ClassPathResource("db/seed/load/v4-expiry.sql");
        POSTGRES.copyFileToContainer(
                Transferable.of(resource.getContentAsByteArray()), "/tmp/v4-expiry.sql");
        var seed =
                POSTGRES.execInContainer(
                        "psql",
                        "-U",
                        POSTGRES.getUsername(),
                        "-d",
                        POSTGRES.getDatabaseName(),
                        "-v",
                        "ON_ERROR_STOP=1",
                        "-v",
                        "profile=demo",
                        "-v",
                        "run_label=pipeline",
                        "-v",
                        "recipient=" + RECIPIENT,
                        "-f",
                        "/tmp/v4-expiry.sql");
        assertThat(seed.getExitCode()).as(seed.getStderr()).isZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM biz_group_deal WHERE deal_no LIKE 'V4-EXPIRY-pipeline-%' AND status='open'"))
                .isEqualTo(2000);
        assertThat(count("SELECT count(*) FROM sys_outbox")).isZero();
        // Do not rewrite expiry timestamps, statuses or event/effect rows: the real 60s
        // deadline, scheduled sweeper, transaction subscribers and relay must do the work.
        await().atMost(Duration.ofSeconds(240))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertThat(
                                    count(
                                            "SELECT count(*) FROM biz_group_deal WHERE deal_no LIKE 'V4-EXPIRY-pipeline-%' AND status='expired'"))
                            .isEqualTo(2000);
                    assertThat(
                                    count(
                                            "SELECT count(*) FROM sys_outbox WHERE event_type='TeamExpired' AND published_at IS NOT NULL AND failed_at IS NULL"))
                            .isEqualTo(2000);
                    assertThat(
                                    count(
                                            "SELECT count(*) FROM biz_notification WHERE notification_type='TeamExpired' AND user_id='"
                                                    + RECIPIENT
                                                    + "'::uuid"))
                            .isEqualTo(2000);
                    assertThat(
                                    count(
                                            "SELECT count(*) FROM sys_consumed_message WHERE consumer='notification'"))
                            .isEqualTo(2000);
                    assertEmpty(queue());
                    assertEmpty(queue() + "-dlq");
                });
        assertThat(count("SELECT count(*) FROM sys_outbox")).isEqualTo(2000);
        assertThat(count("SELECT count(*) FROM biz_notification")).isEqualTo(2000);
        assertThat(count("SELECT count(*) FROM biz_payment")).isZero();
        assertThat(count("SELECT count(*) FROM biz_order")).isZero();
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static void assertEmpty(String url) {
        var attributes =
                sqs.getQueueAttributes(request ->
                                request.queueUrl(url)
                                        .attributeNames(
                                                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                                QueueAttributeName
                                                        .APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                                                QueueAttributeName
                                                        .APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED))
                        .attributes();
        assertThat(attributes).hasSize(3);
        assertThat(attributes.values()).containsOnly("0");
    }

    private static String endpoint() {
        return "http://%s:%d".formatted(ELASTICMQ.getHost(), ELASTICMQ.getMappedPort(9324));
    }

    private static String queue() {
        return endpoint() + "/000000000000/notification-events";
    }
}
