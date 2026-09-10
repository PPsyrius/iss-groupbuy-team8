package com.blocbuy.groupbuy.model;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Accepted server-side terms; no implicit discount, team size or lifetime defaults. */
public record NewDealTerms(
        UUID id,
        String dealNo,
        UUID productId,
        BigDecimal price,
        String currency,
        int targetSize,
        Instant expireTime) {
    /**
     * Validates accepted identifiers and exact SGD price, normalizing expiry to database
     * microseconds.
     */
    public NewDealTerms {
        if (id == null
                || dealNo == null
                || dealNo.isBlank()
                || dealNo.length() > 64
                || productId == null
                || targetSize < 2
                || expireTime == null) {
            throw new BizException(GroupbuyErrorCode.INVALID_PARTICIPATION);
        }
        price = GroupDealMoney.exactSgd(price, currency);
        // PostgreSQL timestamptz stores microseconds; use that same boundary before persistence.
        expireTime = expireTime.truncatedTo(ChronoUnit.MICROS);
    }
}
