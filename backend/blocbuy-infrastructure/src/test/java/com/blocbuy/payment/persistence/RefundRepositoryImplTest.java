package com.blocbuy.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundRepositoryImplTest {
    @Test
    void absentReviewDoesNotCreateAnAuditRecord() {
        var mapper = mock(RefundMapper.class);
        UUID id = UUID.randomUUID();
        assertThat(new RefundRepositoryImpl(mapper).claimReview(id)).isNull();
        verify(mapper).claimReview(id);
        verifyNoMoreInteractions(mapper);
    }
}
