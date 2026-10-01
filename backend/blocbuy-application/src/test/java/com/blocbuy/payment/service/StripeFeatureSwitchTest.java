package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

/** The per-deployment property controls the public Checkout gate. */
class StripeFeatureSwitchTest {
    @Test
    void missingPropertyAndExplicitFalseRemainOff() {
        var feature = new StripeFeatureSwitch(false);
        assertThat(feature.enabled()).isFalse();
        assertThatThrownBy(feature::requireEnabled)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void explicitFlagEnablesTheSameGate() {
        var feature = new StripeFeatureSwitch(true);
        assertThat(feature.enabled()).isTrue();
        feature.requireEnabled();
    }
}
