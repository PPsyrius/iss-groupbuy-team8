package com.blocbuy.bootstrap.fixture;

import com.blocbuy.groupbuy.service.PaidGroupDealService;
import com.blocbuy.payment.service.StripeCheckoutService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Explicit import in the integration test only: no HTTP endpoints or scheduled jobs. */
@TestConfiguration(proxyBeanMethods = false)
public class PaymentFixtureConfiguration {
    @Bean
    PaymentBusinessFixture paymentBusinessFixture(JdbcTemplate jdbc, PaidGroupDealService groups) {
        return new PaymentBusinessFixture(jdbc, groups);
    }

    @Bean
    PaymentLifecycleFixture paymentLifecycleFixture(
            JdbcTemplate jdbc,
            PaymentBusinessFixture business,
            StripeCheckoutService payments,
            PlatformTransactionManager transactions) {
        return new PaymentLifecycleFixture(jdbc, business, payments, transactions);
    }
}
