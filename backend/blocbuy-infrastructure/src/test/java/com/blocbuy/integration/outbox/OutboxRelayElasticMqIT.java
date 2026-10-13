package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.common.model.OutboxEvent;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end local SQS API check against the same ElasticMQ image used by Compose. */
@Testcontainers(disabledWithoutDocker = true)
class OutboxRelayElasticMqIT {

    private static final String QUEUE = "notification-events";

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

    @Test
    void publishesEventsWithAttributesAndRecoversAfterTransportOutage() {
        String endpoint =
                "http://%s:%d".formatted(ELASTICMQ.getHost(), ELASTICMQ.getMappedPort(9324));
        var json = JsonMapper.builder().build();
        var credentials =
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
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
                                .build()) {
            String queueUrl =
                    realClient.listQueues().queueUrls().stream()
                            .filter(url -> url.endsWith("/" + QUEUE))
                            .findFirst()
                            .orElseThrow(
                                    () -> new AssertionError("Compose queue was not configured"));
            List<OutboxEvent> events = events(3);
            var unavailable = new SqsOutboxEventTransport(unavailableClient, queueUrl, json);
            assertThat(unavailable.publish(events))
                    .hasSize(3)
                    .allMatch(result -> result.attempted() && !result.published());

            var recovered = new SqsOutboxEventTransport(realClient, queueUrl, json);
            assertThat(recovered.publish(events)).hasSize(3).allMatch(result -> result.published());

            List<Message> messages =
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
        }
    }

    private static List<OutboxEvent> events(int count) {
        List<OutboxEvent> events = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            events.add(
                    new OutboxEvent(
                            UUID.randomUUID(),
                            "TeamExpired",
                            1,
                            UUID.randomUUID(),
                            "{\"expired\":true}",
                            "trace-it-" + index,
                            Instant.parse("2026-10-12T00:00:00Z"),
                            0));
        }
        return events;
    }
}
