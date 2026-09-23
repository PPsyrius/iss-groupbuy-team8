package com.blocbuy.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.payment.model.RefundWork;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Refund repository claims, finishes and status reads with audit records. */
class RefundRepositoryImplTest {
    private final RefundMapper mapper = mock(RefundMapper.class);
    private final RefundRepositoryImpl repository = new RefundRepositoryImpl(mapper);

    private RefundWork work() {
        return new RefundWork(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                "sgd",
                "pi_fixture",
                "succeeded",
                new BigDecimal("10.00"),
                "sgd",
                null,
                Instant.now(),
                Instant.now(),
                0);
    }

    @Test
    void absentReviewDoesNotCreateAnAuditRecord() {
        UUID id = UUID.randomUUID();
        assertThat(repository.claimReview(id)).isNull();
        verify(mapper).claimReview(id);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void presentReviewIsAuditedAsOperatorReconcile() {
        RefundWork claimed = work();
        UUID id = UUID.randomUUID();
        when(mapper.claimReview(id)).thenReturn(claimed);
        assertThat(repository.claimReview(id)).isSameAs(claimed);
        verify(mapper).claimReview(id);
        verify(mapper).audit(claimed, "OPERATOR_RECONCILE");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void claimedNextAttemptIsAuditedWhileAbsentAttemptIsNot() {
        RefundWork claimed = work();
        when(mapper.claimNext()).thenReturn(claimed).thenReturn(null);
        assertThat(repository.claimNext()).isSameAs(claimed);
        assertThat(repository.claimNext()).isNull();
        verify(mapper, times(2)).claimNext();
        verify(mapper).audit(claimed, "CLAIMED");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void successfulFinishIsAuditedWithResultWhileConflictIsNot() {
        RefundWork claimed = work();
        when(mapper.finish(claimed, "re_fixture", "succeeded", "PROVIDER_SUCCEEDED"))
                .thenReturn(1)
                .thenReturn(0);
        repository.finish(claimed, "re_fixture", "succeeded", "PROVIDER_SUCCEEDED");
        repository.finish(claimed, "re_fixture", "succeeded", "PROVIDER_SUCCEEDED");
        verify(mapper, times(2)).finish(claimed, "re_fixture", "succeeded", "PROVIDER_SUCCEEDED");
        verify(mapper).audit(claimed, "PROVIDER_SUCCEEDED");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void statusDelegatesToMapper() {
        UUID id = UUID.randomUUID();
        when(mapper.status(id)).thenReturn("processing");
        assertThat(repository.status(id)).isEqualTo("processing");
        verify(mapper).status(id);
        verifyNoMoreInteractions(mapper);
    }
}
