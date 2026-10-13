package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxRetentionRepositoryImplTest {
    @Test
    void delegatesExactlyOneDeleteWithTheFixedRowBudget() {
        var mapper = mock(OutboxMapper.class);
        var repository = new OutboxRetentionRepositoryImpl(mapper);
        var cutoff = Instant.parse("2026-10-13T00:00:00Z");
        when(mapper.deletePublishedBefore(cutoff, 500)).thenReturn(500);
        assertThat(repository.deletePublishedBefore(cutoff)).isEqualTo(500);
        verify(mapper).deletePublishedBefore(cutoff, 500);
    }
}
