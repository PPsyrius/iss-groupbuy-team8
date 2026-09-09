package com.blocbuy.groupbuy.model;

import java.time.Instant;

/** Concrete Factory Method creator for the standard paid group-deal product. */
public final class StandardPaidGroupDealCreator extends PaidGroupDealCreator {
    /** Creates a standard OPEN deal whose first paid participant is its leader. */
    @Override
    protected GroupDeal createGroupDeal(
            NewDealTerms terms, PaidParticipation payment, Instant now) {
        return GroupDeal.startPaid(terms, payment, now);
    }
}
