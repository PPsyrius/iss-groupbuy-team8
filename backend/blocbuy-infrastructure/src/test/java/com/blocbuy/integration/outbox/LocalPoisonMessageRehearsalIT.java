package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.events.contract.IntegrationEventEnvelope;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/** Broker-only rehearsal; the production notification consumer and CloudWatch are not exercised. */
@Testcontainers
class LocalPoisonMessageRehearsalIT {
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
    void invalidEnvelopeIsRedrivenAfterFiveReceivesAndGoodMessageStillPasses() throws Exception {
        var json = JsonMapper.builder().build();
        String endpoint =
                "http://%s:%d".formatted(ELASTICMQ.getHost(), ELASTICMQ.getMappedPort(9324));
        try (SqsClient sqs =
                SqsClient.builder()
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("local", "local")))
                        .endpointOverride(URI.create(endpoint))
                        .build()) {
            List<String> queues = sqs.listQueues().queueUrls();
            String queue =
                    queues.stream()
                            .filter(url -> url.endsWith("/notification-events"))
                            .findFirst()
                            .orElseThrow();
            String dlq =
                    queues.stream()
                            .filter(url -> url.endsWith("/notification-events-dlq"))
                            .findFirst()
                            .orElseThrow();
            String policy =
                    sqs.getQueueAttributes(
                                    request ->
                                            request.queueUrl(queue)
                                                    .attributeNames(
                                                            QueueAttributeName.REDRIVE_POLICY))
                            .attributes()
                            .get(QueueAttributeName.REDRIVE_POLICY);
            assertThat(json.readTree(policy).get("maxReceiveCount").asInt()).isEqualTo(5);
            // Accelerate only the local timing; retain the repository's maxReceiveCount=5 policy.
            sqs.setQueueAttributes(
                    request ->
                            request.queueUrl(queue)
                                    .attributes(
                                            Map.of(QueueAttributeName.VISIBILITY_TIMEOUT, "1")));
            String poison = "{\"eventType\":\"PaymentSucceeded\",\"version\":1}";
            String poisonId =
                    sqs.sendMessage(request -> request.queueUrl(queue).messageBody(poison))
                            .messageId();
            List<Integer> receives = new ArrayList<>();
            for (int expected = 1; expected <= 5; expected++) {
                var messages =
                        sqs.receiveMessage(
                                        ReceiveMessageRequest.builder()
                                                .queueUrl(queue)
                                                .maxNumberOfMessages(1)
                                                .waitTimeSeconds(2)
                                                .messageSystemAttributeNames(
                                                        MessageSystemAttributeName
                                                                .APPROXIMATE_RECEIVE_COUNT)
                                                .build())
                                .messages();
                assertThat(messages).hasSize(1);
                var message = messages.get(0);
                assertThat(message.messageId()).isEqualTo(poisonId);
                int count =
                        Integer.parseInt(
                                message.attributes()
                                        .get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT));
                assertThat(count).isEqualTo(expected);
                receives.add(count);
                assertThatThrownBy(
                                () ->
                                        json.readValue(
                                                message.body(), IntegrationEventEnvelope.class))
                        .hasRootCauseInstanceOf(NullPointerException.class);
                // Simulated failed decode: do not acknowledge/delete the message.
                sqs.changeMessageVisibility(
                        request ->
                                request.queueUrl(queue)
                                        .receiptHandle(message.receiptHandle())
                                        .visibilityTimeout(0));
            }
            assertThat(
                            sqs.receiveMessage(
                                            request -> request.queueUrl(queue).waitTimeSeconds(2))
                                    .messages())
                    .isEmpty();
            var dead =
                    sqs.receiveMessage(request -> request.queueUrl(dlq).waitTimeSeconds(2))
                            .messages();
            assertThat(dead).hasSize(1);
            assertThat(dead.get(0).body()).isEqualTo(poison);

            var good =
                    new OutboxEvent(
                            UUID.randomUUID(),
                            "TeamExpired",
                            1,
                            UUID.randomUUID(),
                            "{\"expired\":true}",
                            "local-459-good",
                            Instant.now(),
                            0);
            var transport = new SqsOutboxEventTransport(sqs, queue, json);
            assertThat(transport.publish(List.of(good)))
                    .singleElement()
                    .matches(result -> result.published());
            var goodDelivery =
                    sqs.receiveMessage(request -> request.queueUrl(queue).waitTimeSeconds(2))
                            .messages();
            assertThat(goodDelivery).hasSize(1);
            var decoded =
                    json.readValue(goodDelivery.get(0).body(), IntegrationEventEnvelope.class);
            assertThat(decoded.eventId()).isEqualTo(good.id());
            sqs.deleteMessage(
                    request ->
                            request.queueUrl(queue)
                                    .receiptHandle(goodDelivery.get(0).receiptHandle()));
            var result =
                    Map.of(
                            "issue",
                            459,
                            "recordedAt",
                            Instant.now().toString(),
                            "level",
                            "local ElasticMQ SQS API; real envelope and publisher; simulated failed decode",
                            "maxReceiveCount",
                            5,
                            "observedReceiveCounts",
                            receives,
                            "poisonMessagesInDlq",
                            dead.size(),
                            "goodMessageDecodedAfterPoison",
                            true,
                            "cloudWatchAlarmTested",
                            false,
                            "productionConsumerTested",
                            false,
                            "result",
                            "PASS");
            Path evidence =
                    Path.of(System.getProperty("rehearsal.evidence", "target/rehearsal-evidence"));
            Files.createDirectories(evidence);
            Files.writeString(
                    evidence.resolve("poison-message.json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }
}
