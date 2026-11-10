package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.blocbuy.common.util.UuidV7;
import com.blocbuy.events.contract.IntegrationEventEnvelope;
import com.blocbuy.events.contract.PaymentSucceededV1;
import com.blocbuy.events.contract.TeamExpiredV1;
import com.blocbuy.events.contract.TeamSucceededV1;
import com.blocbuy.notification.messaging.IntegrationEventDecoder;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.json.JsonMapper;

/**
 * The production consumer in the monolith context against ElasticMQ (repository {@code
 * elasticmq.conf}: {@code notification-events}, DLQ after {@code maxReceiveCount = 5}) and
 * PostgreSQL (#446 acceptance criteria). Only the timings are shortened: 1 s long poll and 1 s
 * visibility timeout, so a failed message is redelivered within seconds.
 */
@SpringBootTest(
        properties = {
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "blocbuy.events.consumer.enabled=true",
            "blocbuy.events.consumer.concurrency=2",
            "blocbuy.events.consumer.wait-time=1s",
            "blocbuy.events.consumer.visibility-timeout=1s",
            "blocbuy.events.consumer.shutdown-timeout=2s",
            "blocbuy.events.consumer.error-backoff=200ms",
            "blocbuy.events.region=us-east-1"
        })
@Testcontainers
@Import(OrderControllerTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationConsumerElasticMqIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration DEADLINE = Duration.ofSeconds(60);
    private static final String QUEUE_PATH = "/000000000000/notification-events";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Container
    static final GenericContainer<?> ELASTICMQ =
            new GenericContainer<>("softwaremill/elasticmq-native:1.7.1")
                    .withFileSystemBind(
                            Path.of(System.getProperty("basedir"), "..", "elasticmq.conf")
                                    .toAbsolutePath()
                                    .normalize()
                                    .toString(),
                            "/opt/elasticmq.conf",
                            BindMode.READ_ONLY)
                    .withExposedPorts(9324);

    private static SqsClient sqs;

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
        properties.add("blocbuy.events.sqs.endpoint", NotificationConsumerElasticMqIT::endpoint);
        properties.add("blocbuy.events.sqs.queue-url", () -> endpoint() + QUEUE_PATH);
    }

    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private IntegrationEventDecoder decoder;

    @BeforeAll
    static void credentials() {
        // The consumer uses the SDK default chain, as on ECS; ElasticMQ accepts any key.
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
        sqs.close();
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
    }

    @Test
    void sameEventIdDeliveredThreeTimesCreatesOneNotificationPerRecipient() {
        UUID a = UuidV7.next();
        UUID b = UuidV7.next();
        UUID eventId = UuidV7.next();
        String body =
                envelope(
                        eventId,
                        "TeamSucceeded",
                        new TeamSucceededV1(UuidV7.next(), UuidV7.next(), 2, List.of(a, b)));
        // A relay retry or SNS redelivery: same envelope, a new SQS MessageId each time.
        for (int i = 0; i < 3; i++) {
            send(body, "TeamSucceeded", "1");
        }

        awaitUntil(() -> queueDrained() && notifications(eventId) == 2);

        assertThat(
                        jdbc.queryForList(
                                "SELECT user_id FROM biz_notification WHERE event_id = ?",
                                UUID.class,
                                eventId))
                .containsExactlyInAnyOrder(a, b);
        assertThat(markers(eventId)).isEqualTo(1);
        // At least 3: a slow runner may exceed the 1 s visibility and redeliver one copy, which the
        // ledger then turns into another DUPLICATE; the row counts above are the real assertion.
        verify(decoder, atLeast(3)).decode(body);
    }

    @Test
    void poisonMessageIsDeadLetteredAfterFiveReceivesWhileOtherMessagesAreProcessed() {
        UUID poisonId = UuidV7.next();
        String poison =
                "{\"eventId\":\""
                        + poisonId
                        + "\",\"eventType\":\"PaymentSucceeded\",\"version\":1,\"payload\":{}}";
        UUID expiredId = UuidV7.next();
        UUID paymentId = UuidV7.next();
        String expired =
                envelope(
                        expiredId,
                        "TeamExpired",
                        new TeamExpiredV1(
                                UuidV7.next(),
                                UuidV7.next(),
                                Instant.now(),
                                List.of(UuidV7.next())));
        String payment =
                envelope(
                        paymentId,
                        "PaymentSucceeded",
                        new PaymentSucceededV1(
                                UuidV7.next(),
                                UuidV7.next(),
                                UuidV7.next(),
                                "JOIN",
                                UuidV7.next(),
                                Instant.now()));
        send(poison, "PaymentSucceeded", "1");
        send(expired, "TeamExpired", "1");
        send(payment, "PaymentSucceeded", "1");

        awaitUntil(() -> notifications(expiredId) == 1 && notifications(paymentId) == 1);
        String dlq = endpoint() + QUEUE_PATH + "-dlq";
        Message[] dead = new Message[1];
        awaitUntil(() -> {
            var received =
                    sqs.receiveMessage(request ->
                                    request.queueUrl(dlq).waitTimeSeconds(1).maxNumberOfMessages(1))
                            .messages();
            if (received.isEmpty()) {
                return false;
            }
            dead[0] = received.get(0);
            return true;
        });

        assertThat(dead[0].body()).isEqualTo(poison);
        sqs.deleteMessage(request -> request.queueUrl(dlq).receiptHandle(dead[0].receiptHandle()));
        awaitUntil(this::queueDrained);
        // maxReceiveCount = 5: five failed decodes, then ElasticMQ moves it on the next receive.
        verify(decoder, times(5)).decode(poison);
        assertThat(markers(poisonId)).isZero();
        assertThat(notifications(poisonId)).isZero();
        assertThat(markers(expiredId)).isEqualTo(1);
        assertThat(markers(paymentId)).isEqualTo(1);
    }

    @Test
    void unsupportedVersionProbeIsDeletedWithoutEffect() {
        UUID probeId = UuidV7.next();
        // infra/swe5001/scripts/messaging-check.sh sends this shape.
        String probe =
                "{\"eventId\":\""
                        + probeId
                        + "\",\"eventType\":\"PaymentSucceeded\",\"version\":2147483647,"
                        + "\"payload\":{}}";
        send(probe, "PaymentSucceeded", "2147483647");

        // Drained means visible and in-flight counts are both 0: an undeleted probe would stay in
        // flight or reappear after the visibility timeout, so this proves it was deleted.
        awaitUntil(this::queueDrained);

        verify(decoder, atLeastOnce()).decode(probe);
        assertThat(markers(probeId)).isZero();
        assertThat(notifications(probeId)).isZero();
    }

    private static String endpoint() {
        return "http://%s:%d".formatted(ELASTICMQ.getHost(), ELASTICMQ.getMappedPort(9324));
    }

    private static String envelope(UUID eventId, String type, Object payload) {
        return JSON.writeValueAsString(
                new IntegrationEventEnvelope<>(
                        eventId,
                        type,
                        1,
                        Instant.now(),
                        "blocbuy-commerce",
                        "it-446-" + eventId,
                        UuidV7.next(),
                        payload));
    }

    private static void send(String body, String type, String version) {
        sqs.sendMessage(request ->
                request.queueUrl(endpoint() + QUEUE_PATH)
                        .messageBody(body)
                        .messageAttributes(
                                Map.of(
                                        "eventType", attribute(type),
                                        "version", attribute(version),
                                        "traceId", attribute("it-446-trace"))));
    }

    private static MessageAttributeValue attribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private boolean queueDrained() {
        Map<QueueAttributeName, String> counts =
                sqs.getQueueAttributes(request ->
                                request.queueUrl(endpoint() + QUEUE_PATH)
                                        .attributeNames(
                                                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                                QueueAttributeName
                                                        .APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE))
                        .attributes();
        return "0".equals(counts.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                && "0"
                        .equals(
                                counts.get(
                                        QueueAttributeName
                                                .APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
    }

    private long notifications(UUID eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM biz_notification WHERE event_id = ?", Long.class, eventId);
    }

    private long markers(UUID eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM sys_consumed_message WHERE event_id = ?",
                Long.class,
                eventId);
    }

    private static void awaitUntil(BooleanSupplier condition) {
        await().atMost(DEADLINE)
                .pollInterval(Duration.ofMillis(100))
                .until(condition::getAsBoolean);
    }
}
