package com.blocbuy.groupbuy.service;

import com.blocbuy.groupbuy.event.MemberJoined;
import com.blocbuy.groupbuy.model.ShareCredit;
import com.blocbuy.groupbuy.repository.ShareCreditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UC7 Observer, subscriber side: credits the sharer of an attributed join. The publisher, {@link
 * PaidGroupDealService}, knows nothing about crediting; this class reacts to the immutable {@link
 * MemberJoined} fact and touches only its own repository port.
 *
 * <p><b>Why {@code AFTER_COMMIT}, unlike the team-lifecycle subscribers (backend ADR 0002).</b> A
 * credit is not money. Recorded before the commit, a credit failure would roll back a confirmed
 * payment's admission; the #30 criterion is exactly that crediting must not couple into the join
 * transaction. After the commit, the admission is final whatever happens here.
 *
 * <p>Consequences, all deliberate (backend ADR 0003):
 *
 * <ul>
 *   <li>The write runs in its own {@code REQUIRES_NEW} transaction: the original one has committed,
 *       but its connection is still bound, and a plain write would silently join it with no commit
 *       to follow.
 *   <li>Failures are caught here, including a failed commit — which is why the transaction is an
 *       explicit template inside the {@code try}, not an annotation around it. An exception thrown
 *       after the commit would reach the webhook caller and turn a settled payment into a 500.
 *   <li>A crash between the commit and this method loses the credit, not the attribution: {@code
 *       biz_group_member.sharer_user_id} was committed with the admission and is the source the
 *       ADR's reconciliation query backfills from.
 *   <li>A duplicate delivery is absorbed by the unique participation key.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareCreditSubscriber {

    private final ShareCreditRepository credits;
    private final PlatformTransactionManager transactions;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberJoined(MemberJoined event) {
        if (!event.attributed()) {
            return;
        }
        TransactionTemplate tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(10);
        try {
            Boolean recorded = tx.execute(status -> credits.recordIfAbsent(ShareCredit.of(event)));
            log.info(
                    "Share credit for deal {} payment {}: {}",
                    event.dealId(),
                    event.paymentId(),
                    Boolean.TRUE.equals(recorded) ? "recorded" : "already present");
        } catch (RuntimeException failure) {
            log.warn(
                    "Share credit not recorded for deal {} payment {}; reconcile from membership",
                    event.dealId(),
                    event.paymentId(),
                    failure);
        }
    }
}
