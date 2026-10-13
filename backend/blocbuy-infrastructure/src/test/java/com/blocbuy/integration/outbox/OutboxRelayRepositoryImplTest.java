package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.model.OutboxPublishResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxRelayRepositoryImplTest {

    private final OutboxMapper mapper = mock(OutboxMapper.class);
    private final OutboxRelayRepositoryImpl repository = new OutboxRelayRepositoryImpl(mapper);

    @Test
    void validatesBoundedReadLimitAndDelegatesValidRead() {
        assertThatThrownBy(() -> repository.findDue(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findDue(101))
                .isInstanceOf(IllegalArgumentException.class);
        when(mapper.findDue(100)).thenReturn(List.of());

        assertThat(repository.findDue(100)).isEmpty();

        verify(mapper).findDue(100);
    }

    @Test
    void validatesResultBatchAndDelegatesValidBatch() {
        assertThatThrownBy(() -> repository.recordResults(null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        List<OutboxPublishResult> none = List.of();
        assertThatThrownBy(() -> repository.recordResults(none, 10))
                .isInstanceOf(IllegalArgumentException.class);
        var tooMany =
                java.util.stream.IntStream.range(0, 101)
                        .mapToObj(index -> OutboxPublishResult.published(UUID.randomUUID()))
                        .toList();
        assertThatThrownBy(() -> repository.recordResults(tooMany, 10))
                .isInstanceOf(IllegalArgumentException.class);

        var result = OutboxPublishResult.published(UUID.randomUUID());
        List<OutboxPublishResult> one = List.of(result);
        assertThatThrownBy(() -> repository.recordResults(one, 0))
                .isInstanceOf(IllegalArgumentException.class);
        when(mapper.recordResults(List.of(result), 10)).thenReturn(1);
        assertThat(repository.recordResults(List.of(result), 10)).isEqualTo(1);
        verify(mapper).recordResults(List.of(result), 10);
    }
}
