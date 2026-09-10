package com.blocbuy.groupbuy.model;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Provider-free facts supplied by a trusted payment application collaborator, never by a browser.
 * Construction validates shape, NOT proof of payment. The caller must verify and persist payment,
 * bind the buyer and business intent, and deduplicate application by paymentId in its transaction.
 */
public record PaidParticipation(UUID paymentId, UUID userId, BigDecimal amount, String currency) {
    /** Validates payment and buyer identities plus exact SGD amount; does not verify payment. */
    public PaidParticipation {
        if (paymentId == null || userId == null) {
            throw new BizException(GroupbuyErrorCode.INVALID_PARTICIPATION);
        }
        amount = GroupDealMoney.exactSgd(amount, currency);
    }

    /** Rejects payment facts whose amount or currency differs from the frozen deal terms. */
    void requirePrice(BigDecimal expectedAmount, String expectedCurrency) {
        if (amount.compareTo(expectedAmount) != 0 || !currency.equals(expectedCurrency)) {
            throw new BizException(GroupbuyErrorCode.PAYMENT_MISMATCH);
        }
    }
}
