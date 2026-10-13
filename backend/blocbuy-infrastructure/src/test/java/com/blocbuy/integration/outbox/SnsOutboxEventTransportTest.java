package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.model.OutboxEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;
import software.amazon.awssdk.services.sns.model.PublishBatchResultEntry;

class SnsOutboxEventTransportTest {

    private final SnsClient client = mock(SnsClient.class);
    private final SnsOutboxEventTransport transport =
            new SnsOutboxEventTransport(
                    client,
                    "arn:aws:sns:us-east-1:000000000000:events",
                    tools.jackson.databind.json.JsonMapper.builder().build());

    @Test
    void batchesAtTenAndAddsMessageAttributes() {
        List<OutboxEvent> events = SqsOutboxEventTransportTest.events(11);
        when(client.publishBatch(any(PublishBatchRequest.class)))
                .thenAnswer(
                        call -> {
                            PublishBatchRequest request = call.getArgument(0);
                            return PublishBatchResponse.builder()
                                    .successful(
                                            request.publishBatchRequestEntries().stream()
                                                    .map(
                                                            entry ->
                                                                    PublishBatchResultEntry
                                                                            .builder()
                                                                            .id(entry.id())
                                                                            .messageId(entry.id())
                                                                            .build())
                                                    .toList())
                                    .build();
                        });

        assertThat(transport.publish(events)).hasSize(11).allMatch(result -> result.published());

        ArgumentCaptor<PublishBatchRequest> requests =
                ArgumentCaptor.forClass(PublishBatchRequest.class);
        verify(client, times(2)).publishBatch(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(request -> request.publishBatchRequestEntries().size())
                .containsExactly(10, 1);
        var first = requests.getAllValues().getFirst().publishBatchRequestEntries().getFirst();
        assertThat(first.messageAttributes()).containsKeys("eventType", "version", "traceId");
        assertThat(first.message())
                .contains("\"eventId\"")
                .contains("\"producer\":\"blocbuy-commerce\"");
    }

    @Test
    void recordsPartialFailureAndMissingEntryFailure() {
        List<OutboxEvent> events = SqsOutboxEventTransportTest.events(2);
        when(client.publishBatch(any(PublishBatchRequest.class)))
                .thenReturn(
                        PublishBatchResponse.builder()
                                .failed(
                                        software.amazon.awssdk.services.sns.model
                                                .BatchResultErrorEntry.builder()
                                                .id(events.getFirst().id().toString())
                                                .code("InternalError")
                                                .build())
                                .build());

        assertThat(transport.publish(events))
                .extracting(result -> result.failureReason())
                .containsExactly("InternalError", "SNS batch failure");
    }
}
