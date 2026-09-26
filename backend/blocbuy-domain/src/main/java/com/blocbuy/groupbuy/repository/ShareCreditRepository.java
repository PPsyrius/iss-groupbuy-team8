package com.blocbuy.groupbuy.repository;

import com.blocbuy.groupbuy.model.ShareCredit;

/** Repository port for UC7 referral credits. Implemented in the infrastructure layer. */
public interface ShareCreditRepository {

    /**
     * Records the credit unless its participation already has one, so a duplicate delivery is a
     * no-op rather than a failure.
     *
     * @return true when a new credit was written
     */
    boolean recordIfAbsent(ShareCredit credit);
}
