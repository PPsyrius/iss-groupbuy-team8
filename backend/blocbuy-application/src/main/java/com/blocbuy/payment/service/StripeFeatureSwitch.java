package com.blocbuy.payment.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Shared rollout decision; no database access and no change to the team's configuration files. */
@Component
@RequiredArgsConstructor
public class StripeFeatureSwitch {
    private final Environment environment;

    public boolean enabled() {
        return environment.matchesProfiles("stripe-sprint2");
    }

    public void requireEnabled() {
        if (!enabled()) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
    }
}
