package com.blocbuy.groupbuy.model;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact money rule shared by accepted deal terms and paid participation. */
final class GroupDealMoney {
    private GroupDealMoney() {}

    static BigDecimal exactSgd(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() <= 0 || !"sgd".equals(currency)) {
            throw new BizException(GroupbuyErrorCode.INVALID_PARTICIPATION);
        }
        BigDecimal normalized;
        try {
            normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BizException(GroupbuyErrorCode.INVALID_PARTICIPATION);
        }
        if (normalized.precision() > 18) {
            throw new BizException(GroupbuyErrorCode.INVALID_PARTICIPATION);
        }
        return normalized;
    }
}
