package com.blocbuy.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.event.MemberJoined;
import com.blocbuy.groupbuy.model.ShareCredit;
import com.blocbuy.groupbuy.repository.ShareCreditRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;

/** #30 acceptance: an attributed join credits its sharer; an unattributed join does not. */
class ShareCreditSubscriberTest {

    private static final UUID DEAL = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID PAYMENT = UUID.randomUUID();
    private static final UUID SHARER = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-09-25T10:00:00Z");

    private final ShareCreditRepository credits = mock(ShareCreditRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TransactionStatus status = mock(TransactionStatus.class);
    private final ShareCreditSubscriber subscriber =
            new ShareCreditSubscriber(credits, transactions);

    @Test
    void anAttributedJoinCreditsItsSharerInItsOwnTransaction() {
        when(transactions.getTransaction(any())).thenReturn(status);
        when(credits.recordIfAbsent(any())).thenReturn(true);

        subscriber.onMemberJoined(new MemberJoined(DEAL, PRODUCT, MEMBER, PAYMENT, SHARER, AT));

        verify(credits).recordIfAbsent(new ShareCredit(DEAL, PAYMENT, SHARER, MEMBER, AT));
        verify(transactions)
                .getTransaction(
                        org.mockito.ArgumentMatchers.argThat(
                                (TransactionDefinition definition) ->
                                        definition.getPropagationBehavior()
                                                == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(transactions).commit(status);
    }

    @Test
    void anUnattributedJoinCreditsNobody() {
        subscriber.onMemberJoined(new MemberJoined(DEAL, PRODUCT, MEMBER, PAYMENT, null, AT));

        verifyNoInteractions(credits, transactions);
    }

    @Test
    void aDuplicateDeliveryIsANoOp() {
        when(transactions.getTransaction(any())).thenReturn(status);
        when(credits.recordIfAbsent(any())).thenReturn(false);

        assertThatCode(
                        () ->
                                subscriber.onMemberJoined(
                                        new MemberJoined(
                                                DEAL, PRODUCT, MEMBER, PAYMENT, SHARER, AT)))
                .doesNotThrowAnyException();
    }

    /** After the commit, a failed credit must never surface to the settled webhook. */
    @Test
    void aFailedWriteIsSwallowedAfterTheJoinCommitted() {
        when(transactions.getTransaction(any())).thenReturn(status);
        when(credits.recordIfAbsent(any())).thenThrow(new IllegalStateException("database down"));

        assertThatCode(
                        () ->
                                subscriber.onMemberJoined(
                                        new MemberJoined(
                                                DEAL, PRODUCT, MEMBER, PAYMENT, SHARER, AT)))
                .doesNotThrowAnyException();
        verify(transactions).rollback(status);
    }

    @Test
    void aFailedCommitIsSwallowedToo() {
        when(transactions.getTransaction(any())).thenReturn(status);
        when(credits.recordIfAbsent(any())).thenReturn(true);
        doThrow(new TransactionSystemException("commit failed")).when(transactions).commit(status);

        assertThatCode(
                        () ->
                                subscriber.onMemberJoined(
                                        new MemberJoined(
                                                DEAL, PRODUCT, MEMBER, PAYMENT, SHARER, AT)))
                .doesNotThrowAnyException();
    }
}
