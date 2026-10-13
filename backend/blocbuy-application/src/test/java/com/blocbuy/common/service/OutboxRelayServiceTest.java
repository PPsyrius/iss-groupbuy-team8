package com.blocbuy.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.gateway.OutboxEventTransport;
import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import com.blocbuy.common.repository.OutboxRelayRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxRelayServiceTest {

    private final OutboxRelayRepository repository = mock(OutboxRelayRepository.class);
    private final OutboxEventTransport transport = mock(OutboxEventTransport.class);
    private final OutboxRelayService service = new OutboxRelayService(repository, transport);

    @Test
    void emptyBatchDoesNotCallTransportOrUpdateRepository() {
        when(repository.findDue(100)).thenReturn(List.of());

        assertThat(service.relayBatch()).isZero();

        verify(repository).findDue(100);
    }

    @Test
    void publishesBoundedBatchAndRecordsPerEventResults() {
        var first = event(0);
        var second = event(0);
        List<OutboxEvent> events = List.of(first, second);
        List<OutboxPublishResult> results =
                List.of(
                        OutboxPublishResult.published(first.id()),
                        OutboxPublishResult.failed(second.id(), "Unavailable"));
        when(repository.findDue(100)).thenReturn(events);
        when(transport.publish(events)).thenReturn(results);
        when(repository.recordResults(results)).thenReturn(2);

        assertThat(service.relayBatch()).isEqualTo(2);

        verify(transport).publish(events);
        verify(repository).recordResults(results);
    }

    @Test
    void transportExceptionRetriesEveryItemWithoutLosingTheBatch() {
        var first = event(0);
        var second = event(9);
        List<OutboxEvent> events = List.of(first, second);
        when(repository.findDue(100)).thenReturn(events);
        when(transport.publish(events)).thenThrow(new IllegalStateException("unavailable"));
        when(repository.recordResults(org.mockito.ArgumentMatchers.anyList())).thenReturn(2);

        assertThat(service.relayBatch()).isEqualTo(2);

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(repository).recordResults(captor.capture());
        @SuppressWarnings("unchecked")
        List<OutboxPublishResult> results = captor.getValue();
        assertThat(results)
                .extracting(OutboxPublishResult::eventId)
                .containsExactly(first.id(), second.id());
        assertThat(results).allMatch(result -> !result.published());
    }

    @Test
    void incompleteTransportResultRetriesTheWholeBatch() {
        var event = event(0);
        List<OutboxEvent> events = List.of(event);
        when(repository.findDue(100)).thenReturn(events);
        when(transport.publish(events)).thenReturn(List.of());
        when(repository.recordResults(org.mockito.ArgumentMatchers.anyList())).thenReturn(1);

        assertThat(service.relayBatch()).isEqualTo(1);

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(repository).recordResults(captor.capture());
        @SuppressWarnings("unchecked")
        List<OutboxPublishResult> results = captor.getValue();
        assertThat(results)
                .singleElement()
                .satisfies(result -> assertThat(result.published()).isFalse());
    }

    @Test
    void nullTransportResultRetriesTheWholeBatch() {
        var event = event(0);
        List<OutboxEvent> events = List.of(event);
        when(repository.findDue(100)).thenReturn(events);
        when(transport.publish(events)).thenReturn(null);
        when(repository.recordResults(org.mockito.ArgumentMatchers.anyList())).thenReturn(1);

        assertThat(service.relayBatch()).isEqualTo(1);

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(repository).recordResults(captor.capture());
        @SuppressWarnings("unchecked")
        List<OutboxPublishResult> results = captor.getValue();
        assertThat(results)
                .singleElement()
                .satisfies(result -> assertThat(result.published()).isFalse());
    }

    @Test
    void mismatchedTransportResultIdsRetryTheWholeBatch() {
        var event = event(0);
        List<OutboxEvent> events = List.of(event);
        when(repository.findDue(100)).thenReturn(events);
        when(transport.publish(events))
                .thenReturn(List.of(OutboxPublishResult.published(UUID.randomUUID())));
        when(repository.recordResults(org.mockito.ArgumentMatchers.anyList())).thenReturn(1);

        assertThat(service.relayBatch()).isEqualTo(1);

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(repository).recordResults(captor.capture());
        @SuppressWarnings("unchecked")
        List<OutboxPublishResult> results = captor.getValue();
        assertThat(results)
                .singleElement()
                .satisfies(result -> assertThat(result.published()).isFalse());
    }

    private static OutboxEvent event(int attempts) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "TeamExpired",
                1,
                UUID.randomUUID(),
                "{}",
                "trace-test",
                Instant.parse("2026-10-12T00:00:00Z"),
                attempts);
    }
}
