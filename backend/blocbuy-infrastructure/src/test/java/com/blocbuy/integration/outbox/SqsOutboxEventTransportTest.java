package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.model.OutboxEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import tools.jackson.databind.json.JsonMapper;

class SqsOutboxEventTransportTest {

    private final SqsClient client = mock(SqsClient.class);
    private final SqsOutboxEventTransport transport =
            new SqsOutboxEventTransport(
                    client, "http://elasticmq/queue", JsonMapper.builder().build());

    @Test
    void batchesAtTenAndIncludesEnvelopeAndRequiredAttributes() {
        List<OutboxEvent> events = events(11);
        when(client.sendMessageBatch(any(SendMessageBatchRequest.class)))
                .thenAnswer(
                        call -> {
                            SendMessageBatchRequest request = call.getArgument(0);
                            return SendMessageBatchResponse.builder()
                                    .successful(
                                            request.entries().stream()
                                                    .map(
                                                            entry ->
                                                                    SendMessageBatchResultEntry
                                                                            .builder()
                                                                            .id(entry.id())
                                                                            .build())
                                                    .toList())
                                    .build();
                        });

        assertThat(transport.publish(events)).hasSize(11).allMatch(result -> result.published());

        ArgumentCaptor<SendMessageBatchRequest> requests =
                ArgumentCaptor.forClass(SendMessageBatchRequest.class);
        verify(client, times(2)).sendMessageBatch(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(request -> request.entries().size())
                .containsExactly(10, 1);
        var first = requests.getAllValues().getFirst().entries().getFirst();
        assertThat(first.messageAttributes()).containsKeys("eventType", "version", "traceId");
        assertThat(first.messageAttributes().get("eventType").stringValue())
                .isEqualTo("TeamExpired");
        assertThat(first.messageAttributes().get("version").stringValue()).isEqualTo("1");
        assertThat(first.messageAttributes().get("traceId").stringValue()).isEqualTo("trace-test");
        assertThat(first.messageBody())
                .contains("\"eventId\"")
                .contains("\"eventType\":\"TeamExpired\"")
                .contains("\"version\":1");
    }

    @Test
    void recordsPartialBatchFailurePerEntry() {
        OutboxEvent event = events(1).getFirst();
        when(client.sendMessageBatch(any(SendMessageBatchRequest.class)))
                .thenReturn(
                        SendMessageBatchResponse.builder()
                                .failed(
                                        software.amazon.awssdk.services.sqs.model
                                                .BatchResultErrorEntry.builder()
                                                .id(event.id().toString())
                                                .code("InternalError")
                                                .build())
                                .build());

        assertThat(transport.publish(List.of(event)))
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.published()).isFalse();
                            assertThat(result.failureReason()).isEqualTo("InternalError");
                        });
    }

    static List<OutboxEvent> events(int count) {
        List<OutboxEvent> events = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            events.add(
                    new OutboxEvent(
                            UUID.randomUUID(),
                            "TeamExpired",
                            1,
                            UUID.randomUUID(),
                            "{\"reason\":\"expired\"}",
                            "trace-test",
                            Instant.parse("2026-10-12T00:00:00Z"),
                            0));
        }
        return events;
    }
}
