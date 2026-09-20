package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StripeFeatureSwitchTest {
    @Test
    void defaultAndUnrelatedProfilesRemainOff() {
        var environment = new MockEnvironment();
        var feature = new StripeFeatureSwitch(environment);
        assertThat(feature.enabled()).isFalse();
        environment.setActiveProfiles("local", "docker", "ec2");
        assertThat(feature.enabled()).isFalse();
        assertThatThrownBy(feature::requireEnabled)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void explicitStripeProfileEnablesTheSameGate() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local", "stripe-sprint2");
        var feature = new StripeFeatureSwitch(environment);
        assertThat(feature.enabled()).isTrue();
        feature.requireEnabled();
    }
}
