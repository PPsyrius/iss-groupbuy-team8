package com.blocbuy.groupbuy.model;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import java.time.Instant;

/**
 * UC3 creator: the initiation workflow validates accepted terms before invoking its factory method.
 * Persistence of the returned aggregate and first member belongs to one application transaction.
 * UC4 does not use this creator and cannot create a replacement deal.
 */
public abstract class PaidGroupDealCreator {
    public final GroupDeal initiate(NewDealTerms terms, PaidParticipation payment, Instant now) {
        if (terms == null || payment == null || now == null || !now.isBefore(terms.expireTime())) {
            throw new BizException(GroupbuyErrorCode.INVALID_PARTICIPATION);
        }
        payment.requirePrice(terms.price(), terms.currency());
        return createGroupDeal(terms, payment, now);
    }

    protected abstract GroupDeal createGroupDeal(
            NewDealTerms terms, PaidParticipation payment, Instant now);
}
