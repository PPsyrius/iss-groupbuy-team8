package com.blocbuy.payment.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;

/** Shared rollout decision from the bound deployment property; no database access. */
@RequiredArgsConstructor
public class StripeFeatureSwitch {
    private final boolean checkoutEnabled;

    public boolean enabled() {
        return checkoutEnabled;
    }

    public void requireEnabled() {
        if (!enabled()) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
    }
}
